package com.alan.clients.util.file.config;

import com.alan.clients.Client;
import com.alan.clients.bindable.Bindable;
import com.alan.clients.component.impl.render.NotificationComponent;
import com.alan.clients.event.impl.other.ConfigLoadEvent;
import com.alan.clients.module.Module;
import com.alan.clients.module.impl.render.ClickGUI;
import com.alan.clients.util.file.FileType;
import com.alan.clients.value.Mode;
import com.alan.clients.value.Value;
import com.alan.clients.value.impl.*;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.Setter;

import java.awt.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
public class ConfigFile extends com.alan.clients.util.file.File implements Bindable {

    private static final SimpleDateFormat DATE_FORMATTER = new SimpleDateFormat("dd.MM.yyyy");

    private boolean saveKeyCodes;
    private String name;
    @Setter
    private int key;

    public ConfigFile(final java.io.File file, final FileType fileType, final String name) {
        super(file, fileType);
        this.name = name;
    }

    public ConfigFile(final java.io.File file, final FileType fileType) {
        super(file, fileType);
    }

    @Override
    public boolean read() {
        if (!this.getFile().exists()) {
            return false;
        }

        try {
            final FileReader fileReader = new FileReader(getFile());
            final BufferedReader bufferedReader = new BufferedReader(fileReader);
            final JsonObject jsonObject = getGSON().fromJson(bufferedReader, JsonObject.class);

            bufferedReader.close();
            fileReader.close();

            if (jsonObject == null) {
                return false;
            }

            for (final Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
                final String key = entry.getKey();

                if (key.equals("Metadata") || key.equals("theme")) continue;

                final JsonObject moduleJson = entry.getValue().getAsJsonObject();

                for (final Module module : Client.INSTANCE.getModuleManager().getAll()) {
                    if (module.getModuleInfo().aliases()[0].equals(key)) {
                        if (moduleJson.has("state") && !(module instanceof ClickGUI)) {
                            if (moduleJson.get("state").getAsBoolean() != module.isEnabled()) {
                                module.toggle();
                            }
                        }

                        if (moduleJson.has("keyCode")) {
                            module.setKey(moduleJson.get("keyCode").getAsInt());
                        }

                        for (final Value<?> value : module.getAllValues()) {
                            final String valueKey = value.getName() + " in " +
                                    (value.getParent() == null ? "Unknown" :
                                            value.getParent() instanceof Module ?
                                                    ((Module) value.getParent()).getModuleInfo().aliases()[0] + " Module" :
                                                    ((Mode<?>) value.getParent()).getName() + " Mode");

                            if (!moduleJson.has(valueKey)) continue;

                            final JsonObject valueJson = moduleJson.get(valueKey).getAsJsonObject();

                            if (value instanceof ModeValue && valueJson.has("value")) {
                                final String modeName = valueJson.get("value").getAsString();
                                for (final Mode<?> mode : ((ModeValue) value).getModes()) {
                                    if (mode.getName().equals(modeName)) {
                                        ((ModeValue) value).setValue(mode);
                                        break;
                                    }
                                }
                            } else if (value instanceof BooleanValue && valueJson.has("value")) {
                                ((BooleanValue) value).setValue(valueJson.get("value").getAsBoolean());
                            } else if (value instanceof NumberValue && valueJson.has("value")) {
                                ((NumberValue) value).setValue(valueJson.get("value").getAsDouble());
                            } else if (value instanceof StringValue && valueJson.has("value")) {
                                String loaded = valueJson.get("value").getAsString();
                                loaded = loaded.replace("<percentsign>", "%");
                                ((StringValue) value).setValue(loaded);
                            } else if (value instanceof BoundsNumberValue && valueJson.has("first")) {
                                ((BoundsNumberValue) value).setValue(valueJson.get("first").getAsDouble());
                                ((BoundsNumberValue) value).setSecondValue(valueJson.get("second").getAsDouble());
                            } else if (value instanceof ColorValue && valueJson.has("red")) {
                                ((ColorValue) value).setValue(new Color(
                                        valueJson.get("red").getAsInt(),
                                        valueJson.get("green").getAsInt(),
                                        valueJson.get("blue").getAsInt(),
                                        valueJson.get("alpha").getAsInt()));
                            } else if (value instanceof DragValue && valueJson.has("positionX")) {
                                ((DragValue) value).position.x = valueJson.get("positionX").getAsDouble();
                                ((DragValue) value).position.y = valueJson.get("positionY").getAsDouble();
                                ((DragValue) value).scale.x = valueJson.get("scaleX").getAsDouble();
                                ((DragValue) value).scale.y = valueJson.get("scaleY").getAsDouble();
                            }
                        }
                        break;
                    }
                }
            }
        } catch (final Exception ignored) {
            return false;
        }

        Client.INSTANCE.getEventBus().handle(new ConfigLoadEvent());

        if (name != null) NotificationComponent.post("Config", "Loaded " + name + " config");

        return true;
    }

    @Override
    public boolean write() {
        try {
            // creates the file
            this.getFile().createNewFile();
            // creates a new json object where all data is stored in
            final JsonObject jsonObject = new JsonObject();

            // Add some extra information to the config
            final JsonObject metadataJsonObject = new JsonObject();
            metadataJsonObject.addProperty("version", Client.VERSION);
            metadataJsonObject.addProperty("creationDate", DATE_FORMATTER.format(new Date()));
            jsonObject.add("Metadata", metadataJsonObject);

            // loops through all modules to save their data
            for (final Module module : Client.INSTANCE.getModuleManager().getAll()) {
                // creates an own module json object
                final JsonObject moduleJsonObject = new JsonObject();

                // adds data to the module json object
                if (!(module instanceof ClickGUI)) {
                    moduleJsonObject.addProperty("state", module.isEnabled());
                }

                if (saveKeyCodes) moduleJsonObject.addProperty("keyCode", module.getKey());

                int index = 0;
                for (final Value<?> value : module.getAllValues()) {
                    index++;
                    final JsonObject valueJsonObject = new JsonObject();

                    if (value instanceof ModeValue) {
                        final ModeValue enumValue = (ModeValue) value;
                        valueJsonObject.addProperty("value", enumValue.getValue().getName());
                    } else if (value instanceof BooleanValue) {
                        final BooleanValue booleanValue = (BooleanValue) value;
                        valueJsonObject.addProperty("value", booleanValue.getValue());
                    } else if (value instanceof NumberValue) {
                        final NumberValue numberValue = (NumberValue) value;
                        valueJsonObject.addProperty("value", numberValue.getValue().doubleValue());
                    } else if (value instanceof StringValue) {
                        final StringValue stringValue = (StringValue) value;

                        String save = stringValue.getValue();
                        save = save.replace("%", "<percentsign>");

                        valueJsonObject.addProperty("value", save);
                    } else if (value instanceof BoundsNumberValue) {
                        final BoundsNumberValue boundsNumberValue = (BoundsNumberValue) value;
                        valueJsonObject.addProperty("first", boundsNumberValue.getValue().doubleValue());
                        valueJsonObject.addProperty("second", boundsNumberValue.getSecondValue().doubleValue());
                    } else if (value instanceof ColorValue) {
                        final ColorValue colorValue = (ColorValue) value;

                        valueJsonObject.addProperty("red", colorValue.getValue().getRed());
                        valueJsonObject.addProperty("green", colorValue.getValue().getGreen());
                        valueJsonObject.addProperty("blue", colorValue.getValue().getBlue());
                        valueJsonObject.addProperty("alpha", colorValue.getValue().getAlpha());
                    } else if (value instanceof DragValue) {
                        final DragValue positionValue = (DragValue) value;

                        valueJsonObject.addProperty("positionX", positionValue.position.x);
                        valueJsonObject.addProperty("positionY", positionValue.position.y);

                        valueJsonObject.addProperty("scaleX", positionValue.scale.x);
                        valueJsonObject.addProperty("scaleY", positionValue.scale.y);
                    } else if (value instanceof ListValue) {
                        final ListValue<?> enumValue = (ListValue<?>) value;
                        valueJsonObject.addProperty("value", enumValue.getValue().toString());
                    }

                    String name = value.getParent() != null ? (value.getParent() instanceof Module ? ((Module) value.getParent()).getModuleInfo().aliases()[0] + " Module" :
                            ((Mode<?>) value.getParent()).getName() + " Mode") : "Unknown";

                    moduleJsonObject.add(value.getName() + " in " + name, valueJsonObject);
                }

                // updates json object which contains all data
                jsonObject.add(module.getModuleInfo().aliases()[0], moduleJsonObject);
            }

            jsonObject.addProperty("theme", Client.INSTANCE.getThemeManager().getTheme().name());

            // writes json object data to a file
            final FileWriter fileWriter = new FileWriter(getFile());
            final BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
            getGSON().toJson(jsonObject, bufferedWriter);

            // closes the writer
            bufferedWriter.flush();
            bufferedWriter.close();
//            fileWriter.flush();
//            fileWriter.close();
        } catch (final IOException exception) {
            exception.printStackTrace();
            return false;
        }

        return true;
    }

    public void saveKeyCodes() {
        this.saveKeyCodes = true;
    }

    @Override
    public int getKey() {
        return key;
    }

    @Override
    public void onKey() {
        this.read();
    }

    @Override
    public String[] getAliases() {
        return new String[]{getName()};
    }

    @Override
    public String getName() {
        return name;
    }
}
