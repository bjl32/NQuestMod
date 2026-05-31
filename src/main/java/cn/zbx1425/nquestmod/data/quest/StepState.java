package cn.zbx1425.nquestmod.data.quest;

import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class StepState {

    private Map<String, JsonObject> nodeStates = new HashMap<>();
    private transient boolean dirty = false;

    public JsonObject getOrCreate(String path, Supplier<JsonObject> defaultState) {
        return nodeStates.computeIfAbsent(path, k -> defaultState.get());
    }

    public JsonObject get(String path) {
        return nodeStates.get(path);
    }

    public boolean getBoolean(String path, String key, boolean defaultValue) {
        JsonObject node = nodeStates.get(path);
        if (node == null || !node.has(key)) return defaultValue;
        return node.get(key).getAsBoolean();
    }

    public void setBoolean(String path, String key, boolean value) {
        JsonObject node = getOrCreate(path, JsonObject::new);
        if (!node.has(key) || node.get(key).getAsBoolean() != value) {
            node.addProperty(key, value);
            dirty = true;
        }
    }

    public double getDouble(String path, String key, double defaultValue) {
        JsonObject node = nodeStates.get(path);
        if (node == null || !node.has(key)) return defaultValue;
        return node.get(key).getAsDouble();
    }

    public void setDouble(String path, String key, double value) {
        JsonObject node = getOrCreate(path, JsonObject::new);
        if (!node.has(key) || node.get(key).getAsDouble() != value) {
            node.addProperty(key, value);
            dirty = true;
        }
    }

    public int getInt(String path, String key, int defaultValue) {
        JsonObject node = nodeStates.get(path);
        if (node == null || !node.has(key)) return defaultValue;
        return node.get(key).getAsInt();
    }

    public void setInt(String path, String key, int value) {
        JsonObject node = getOrCreate(path, JsonObject::new);
        if (!node.has(key) || node.get(key).getAsInt() != value) {
            node.addProperty(key, value);
            dirty = true;
        }
    }

    public boolean consumeDirty() {
        boolean result = dirty;
        dirty = false;
        return result;
    }
}
