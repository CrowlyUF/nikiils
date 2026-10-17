package com.alan.clients.event.bus.impl;

import com.alan.clients.Client;
import com.alan.clients.event.Listener;
import com.alan.clients.event.annotations.EventLink;
import com.alan.clients.event.bus.Bus;
import com.alan.clients.event.impl.other.*;
import com.alan.clients.event.impl.packet.PacketSendEvent;
import com.alan.clients.event.impl.render.RenderGUIEvent;
import com.alan.clients.util.Accessor;
import com.alan.clients.util.chat.ChatUtil;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;

/**
 * Rise's annotation event bus.
 * <p>
 * Compared to the original: the reflected {@code @EventLink} fields are cached per class (modules re-register on every
 * toggle), each event type keeps a copy-on-write array so registering only rebuilds the types it touches, dispatch is
 * safe from the netty thread, and a throwing listener no longer stops the listeners after it.
 */
public final class EventBus<Event> implements Bus<Event>, Accessor {

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
    @SuppressWarnings("rawtypes")
    private static final Listener[] NONE = new Listener[0];

    /** event class -> call sites, highest priority first */
    private final Map<Type, CallSite<Event>[]> callSites = new ConcurrentHashMap<>();
    /** event class -> listeners in dispatch order (derived from callSites) */
    private final Map<Type, Listener<Event>[]> listeners = new ConcurrentHashMap<>();
    private final Map<Class<?>, LinkSpec[]> linkSpecs = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<Function<Event, Boolean>> customListeners = new ConcurrentLinkedQueue<>();

    @Override
    public synchronized void register(final Object subscriber) {
        for (final LinkSpec spec : specsOf(subscriber.getClass())) {
            try {
                @SuppressWarnings("unchecked") final Listener<Event> listener = (Listener<Event>) spec.getter.invoke(subscriber);

                final CallSite<Event>[] current = callSites.get(spec.eventType);
                final CallSite<Event>[] updated = insert(current, new CallSite<>(subscriber, listener, spec.priority));
                callSites.put(spec.eventType, updated);
                listeners.put(spec.eventType, toListeners(updated));
            } catch (final Throwable exception) {
                if (Client.DEVELOPMENT_SWITCH) {
                    ChatUtil.display("Exception in console");
                }
                exception.printStackTrace();
            }
        }
    }

    @Override
    public synchronized void unregister(final Object subscriber) {
        for (final LinkSpec spec : specsOf(subscriber.getClass())) {
            final CallSite<Event>[] current = callSites.get(spec.eventType);
            if (current == null) continue;

            final List<CallSite<Event>> kept = new ArrayList<>(current.length);
            for (final CallSite<Event> site : current) {
                if (site.owner != subscriber) kept.add(site);
            }

            if (kept.size() == current.length) continue;

            @SuppressWarnings("unchecked") final CallSite<Event>[] updated = kept.toArray(new CallSite[0]);
            callSites.put(spec.eventType, updated);
            listeners.put(spec.eventType, toListeners(updated));
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void handle(final Event event) {
        if (!shouldDispatch(event)) {
            return;
        }

        final Listener<Event>[] targets = listeners.getOrDefault(event.getClass(), NONE);

        for (final Listener<Event> listener : targets) {
            try {
                listener.call(event);
            } catch (final Throwable exception) {
                report(exception);
            }
        }

        if (!this.customListeners.isEmpty()) {
            try {
                this.customListeners.removeIf(listener -> listener.apply(event));
            } catch (final Throwable exception) {
                report(exception);
            }
        }
    }

    public void registerCustom(final Function<Event, Boolean> listener) {
        this.customListeners.add(listener);
    }

    /**
     * Same gate as Rise: in-game events are dropped until the world and terrain are loaded; a few lifecycle and GUI
     * events always go through.
     */
    private static boolean shouldDispatch(final Object event) {
        if (mc.theWorld != null && mc.getNetHandler() != null && (mc.getNetHandler().doneLoadingTerrain || event instanceof PacketSendEvent)) {
            return true;
        }

        return event instanceof RenderGUIEvent || event instanceof ServerKickEvent || event instanceof GameEvent
                || event instanceof WorldChangeEvent || event instanceof ServerJoinEvent;
    }

    /** Listener exceptions since startup (read by the dev self-test). */
    public static final java.util.concurrent.atomic.AtomicInteger ERRORS = new java.util.concurrent.atomic.AtomicInteger();

    private static void report(final Throwable exception) {
        ERRORS.incrementAndGet();
        exception.printStackTrace();

        if (Client.DEVELOPMENT_SWITCH) {
            ChatUtil.display("Exception in console");
        }
    }

    private LinkSpec[] specsOf(final Class<?> type) {
        return linkSpecs.computeIfAbsent(type, clazz -> {
            final List<LinkSpec> specs = new ArrayList<>();

            for (final Field field : clazz.getDeclaredFields()) {
                final EventLink annotation = field.getAnnotation(EventLink.class);
                if (annotation == null) continue;

                try {
                    field.setAccessible(true);
                    final Type eventType = ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0];
                    specs.add(new LinkSpec(eventType, LOOKUP.unreflectGetter(field), annotation.value()));
                } catch (final Throwable exception) {
                    exception.printStackTrace();
                }
            }

            return specs.toArray(new LinkSpec[0]);
        });
    }

    @SuppressWarnings("unchecked")
    private static <E> CallSite<E>[] insert(final CallSite<E>[] current, final CallSite<E> site) {
        final int length = current == null ? 0 : current.length;
        final CallSite<E>[] updated = new CallSite[length + 1];

        // stable: a new listener goes after existing ones of the same priority
        int index = 0;
        while (index < length && current[index].priority >= site.priority) {
            updated[index] = current[index];
            index++;
        }

        updated[index] = site;

        if (length - index > 0) {
            System.arraycopy(current, index, updated, index + 1, length - index);
        }

        return updated;
    }

    @SuppressWarnings("unchecked")
    private static <E> Listener<E>[] toListeners(final CallSite<E>[] sites) {
        final Listener<E>[] result = new Listener[sites.length];
        for (int i = 0; i < sites.length; i++) result[i] = sites[i].listener;
        return result;
    }

    private static final class LinkSpec {
        private final Type eventType;
        private final MethodHandle getter;
        private final byte priority;

        private LinkSpec(final Type eventType, final MethodHandle getter, final byte priority) {
            this.eventType = eventType;
            this.getter = getter;
            this.priority = priority;
        }
    }

    private static final class CallSite<Event> {
        private final Object owner;
        private final Listener<Event> listener;
        private final byte priority;

        private CallSite(final Object owner, final Listener<Event> listener, final byte priority) {
            this.owner = owner;
            this.listener = listener;
            this.priority = priority;
        }
    }
}
