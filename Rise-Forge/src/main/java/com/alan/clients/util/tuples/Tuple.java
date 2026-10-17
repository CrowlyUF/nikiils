package com.alan.clients.util.tuples;

/**
 * Mutable pair. Rise patched {@code net.minecraft.util.Tuple} to add setters; this keeps that API without touching
 * the vanilla class.
 */
public class Tuple<A, B> {

    private A first;
    private B second;

    public Tuple(final A first, final B second) {
        this.first = first;
        this.second = second;
    }

    public A getFirst() {
        return first;
    }

    public void setFirst(final A first) {
        this.first = first;
    }

    public B getSecond() {
        return second;
    }

    public void setSecond(final B second) {
        this.second = second;
    }
}
