package org.labkey.mcp;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.PropertyManager;

import java.util.Map;

public class McpSettings
{
    private static final String CATEGORY = "McpSettings";
    private static final String API_KEY = "apiKey";
    private static final String MODEL_NAME = "modelName";
    private static final String DEFAULT_MODEL = "claude-sonnet-4-20250514";

    private McpSettings()
    {
    }

    public static @Nullable String getApiKey()
    {
        return getProperties().get(API_KEY);
    }

    public static @NotNull String getModelName()
    {
        String model = getProperties().get(MODEL_NAME);
        return model != null && !model.isBlank() ? model : DEFAULT_MODEL;
    }

    public static boolean isConfigured()
    {
        String key = getApiKey();
        return key != null && !key.isBlank();
    }

    public static void save(String apiKey, String modelName)
    {
        var props = PropertyManager.getEncryptedStore().getWritableProperties(ContainerManager.getRoot(), CATEGORY, true);
        props.put(API_KEY, apiKey);
        props.put(MODEL_NAME, modelName != null && !modelName.isBlank() ? modelName : DEFAULT_MODEL);
        props.save();
    }

    private static @NotNull Map<String, String> getProperties()
    {
        return PropertyManager.getEncryptedStore().getProperties(ContainerManager.getRoot(), CATEGORY);
    }
}
