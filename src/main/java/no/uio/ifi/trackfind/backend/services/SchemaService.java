package no.uio.ifi.trackfind.backend.services;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixProperty;
import lombok.extern.slf4j.Slf4j;
import no.uio.ifi.trackfind.backend.configuration.TrackFindProperties;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.everit.json.schema.*;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Service for loading JSON schema and for validating JSON objects.
 */
@Slf4j
@Service
public class SchemaService {

    public static final String SCHEMA_URL = "https://raw.githubusercontent.com/fairtracks/fairtracks_standard/master/json/schema/fairtracks.schema.json";
    protected TrackFindProperties properties;

    private Schema schema;
    private Multimap<String, String> attributes = HashMultimap.create();

    @Autowired
    public SchemaService(TrackFindProperties properties) {
        this.properties = properties;
        try (InputStream inputStream = new URL(SCHEMA_URL).openStream()) {
            JSONObject rawSchema = new JSONObject(new JSONTokener(inputStream));
            this.schema = SchemaLoader.load(rawSchema);
            gatherAttributes(null, "", schema);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @HystrixCommand(commandProperties = {@HystrixProperty(name = "execution.timeout.enabled", value = "false")})
    private void gatherAttributes(String objectType, String path, Schema schema) {
        if (schema instanceof ObjectSchema) {
            Map<String, Schema> propertySchemas = ((ObjectSchema) schema).getPropertySchemas();
            Set<Map.Entry<String, Schema>> entries = propertySchemas.entrySet();
            if (CollectionUtils.isNotEmpty(entries)) {
                for (Map.Entry<String, Schema> entry : entries) {
                    if (StringUtils.isNotEmpty(objectType)) {
                        gatherAttributes(objectType,
                                path + properties.getLevelsSeparator() + entry.getKey(),
                                entry.getValue());
                    } else {
                        gatherAttributes(entry.getKey(),
                                path,
                                entry.getValue());
                    }
                }
            }
            return;
        }
        if (schema instanceof ArraySchema) {
            Schema allItemSchema = ((ArraySchema) schema).getAllItemSchema();
            if (allItemSchema != null) {
                gatherAttributes(objectType, path, allItemSchema);
            }
            Schema containedItemSchema = ((ArraySchema) schema).getContainedItemSchema();
            if (containedItemSchema != null) {
                gatherAttributes(objectType, path, containedItemSchema);
            }
            return;
        }
        if (schema instanceof CombinedSchema) {
            Collection<Schema> subschemas = ((CombinedSchema) schema).getSubschemas();
            if (CollectionUtils.isNotEmpty(subschemas)) {
                for (Schema subschema : subschemas) {
                    gatherAttributes(objectType, path, subschema);
                }
            }
            return;
        }
        if (schema instanceof ReferenceSchema) {
            gatherAttributes(objectType, path, ((ReferenceSchema) schema).getReferredSchema());
            return;
        }
        if (StringUtils.isNotEmpty(objectType)) {
            int separatorLength = properties.getLevelsSeparator().length();
            attributes.put(objectType, path.isEmpty() ? path : path.substring(separatorLength));
        }
    }

    public Schema getSchema() {
        return schema;
    }

    /**
     * Returns attributes from JSON schema.
     *
     * @return Collection of attributes.
     */
    @HystrixCommand(commandProperties = {@HystrixProperty(name = "execution.timeout.enabled", value = "false")})
    public Map<String, Collection<String>> getAttributes() {
        return Collections.unmodifiableMap(attributes.asMap());
    }

    /**
     * Validates JSON object against the schema. Throws the exception if the object is invalid.
     *
     * @param object Object to validate.
     */
    @HystrixCommand(commandProperties = {@HystrixProperty(name = "execution.timeout.enabled", value = "false")})
    public void validate(Object object) {
        this.schema.validate(object);
    }

}
