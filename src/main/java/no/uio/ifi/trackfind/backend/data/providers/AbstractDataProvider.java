package no.uio.ifi.trackfind.backend.data.providers;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import no.uio.ifi.trackfind.backend.configuration.TrackFindProperties;
import no.uio.ifi.trackfind.backend.events.DataReloadEvent;
import no.uio.ifi.trackfind.backend.operations.Operation;
import no.uio.ifi.trackfind.backend.pojo.TfHub;
import no.uio.ifi.trackfind.backend.pojo.TfObject;
import no.uio.ifi.trackfind.backend.pojo.TfObjectType;
import no.uio.ifi.trackfind.backend.pojo.TfVersion;
import no.uio.ifi.trackfind.backend.repositories.*;
import no.uio.ifi.trackfind.backend.scripting.ScriptingEngine;
import no.uio.ifi.trackfind.backend.services.CacheService;
import no.uio.ifi.trackfind.backend.services.SearchService;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.transaction.Transactional;
import java.util.*;
import java.util.concurrent.ExecutorService;

/**
 * Abstract class for all data providers.
 * Implements some common logic like getting metamodel, searching, etc.
 *
 * @author Dmytro Titov
 */
@Slf4j
public abstract class AbstractDataProvider implements DataProvider {

    protected TrackFindProperties properties;
    protected ApplicationEventPublisher applicationEventPublisher;
    protected CacheService cacheService;
    protected SearchService searchService;
    protected JdbcTemplate jdbcTemplate;
    protected HubRepository hubRepository;
    protected ObjectTypeRepository objectTypeRepository;
    protected VersionRepository versionRepository;
    protected ObjectRepository objectRepository;
    protected MappingRepository mappingRepository;
    protected ExecutorService executorService;
    protected Gson gson;
    protected Collection<ScriptingEngine> scriptingEngines;

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return getClass().getSimpleName().replace("DataProvider", "");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<TfHub> getAllTrackHubs() {
        return Collections.singleton(new TfHub(getName(), getName()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<TfHub> getActiveTrackHubs() {
        return hubRepository.findByRepository(getName());
    }

    /**
     * Fetches data from the repository.
     *
     * @throws Exception in case of some problems.
     */
    protected abstract void fetchData(String hubName) throws Exception;

    /**
     * {@inheritDoc}
     */
    @CacheEvict(cacheNames = {
            "metamodel-array-of-objects-attributes",
            "metamodel-flat",
            "metamodel-tree",
            "metamodel-attribute-types",
            "metamodel-attributes",
            "metamodel-subattributes",
            "metamodel-values"
    }, allEntries = true)
    @Transactional
    @Override
    public synchronized void crawlRemoteRepository(String hubName) {
        log.info("Fetching data using for {}: {}", getName(), hubName);
        try {
            fetchData(hubName);
            applicationEventPublisher.publishEvent(new DataReloadEvent(getName(), Operation.CRAWLING));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return;
        }
        log.info("Success!");
    }

    /**
     * Saves datasets to the database.
     *
     * @param hubName Hub name.
     * @param objects Object-type to object map.
     */
    protected void save(String hubName, Map<String, Collection<String>> objects) {
        TfHub hub = hubRepository.findByNameAndRepository(getName(), hubName);
        String maxVersion = jdbcTemplate.queryForObject("SELECT MAX(v.version) FROM tf_versions v, tf_objects o, tf_hubs h WHERE h.id = ? AND h.id = o.hub_id AND o.version_id = v.id", String.class, hub.getId());
        if (maxVersion == null) {
            maxVersion = "0";
        }
        TfVersion tfVersion = new TfVersion();
        tfVersion.setVersion(String.valueOf(Long.parseLong(maxVersion) + 1));
        tfVersion.setOperation(Operation.CRAWLING);
        tfVersion.setUsername("admin");
        tfVersion.setTime(new Date());
        versionRepository.save(tfVersion);
        Collection<TfObject> objectsToSave = new ArrayList<>();
        for (String objectTypeName : objects.keySet()) {
            TfObjectType objectType = objectTypeRepository.findByNameAndHubId(objectTypeName, hub.getId());
            if (objectType == null) {
                objectType = new TfObjectType();
                objectType.setHub(hub);
                objectType.setName(objectTypeName);
                objectType = objectTypeRepository.save(objectType);
            }
            for (String obj : objects.get(objectTypeName)) {
                TfObject tfObject = new TfObject();
                tfObject.setHub(hub);
                tfObject.setObjectType(objectType);
                tfObject.setVersion(tfVersion);
                tfObject.setContent(obj);
                objectsToSave.add(tfObject);
            }
        }
        objectRepository.saveAll(objectsToSave);
//        applyAutomaticMappings(hub, objectRepository.saveAll(objectsToSave));
    }

//    @Transactional
//    protected synchronized void applyAutomaticMappings(TfHub hub, Collection<TfObject> objects) {
//        Collection<TfObject> objectsToSave = new ArrayList<>();
//        for (TfObject obj : objects) {
//            String idMappingAttribute = hub.getIdMappingAttribute();
//            String versionMappingAttribute = hub.getVersionMappingAttribute();
//            TfObject standard = new TfObject();
//            standard.setId(obj.getId());
//            standard.setRawVersion(obj.getRawVersion());
//            standard.setCuratedVersion(obj.getCuratedVersion());
//            standard.setStandardVersion(1L);
//            Map<String, Object> standardMap = new HashMap<>();
//            putValueByPath(standardMap, idMappingAttribute.split(properties.getLevelsSeparator()), Collections.singleton(standard.getId().toString()));
//            putValueByPath(standardMap, versionMappingAttribute.split(properties.getLevelsSeparator()), Collections.singleton(obj.getRawVersion() + ".0.1"));
//            standard.setContent(gson.toJson(standardMap));
//            objectsToSave.add(standard);
//        }
//        objectRepository.saveAll(objectsToSave);
//    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    @CacheEvict(cacheNames = {
            "metamodel-array-of-objects-attributes",
            "metamodel-flat",
            "metamodel-tree",
            "metamodel-attribute-types",
            "metamodel-attributes",
            "metamodel-subattributes",
            "metamodel-values"
    }, allEntries = true)
    @Transactional
    @Override
    public synchronized void applyMappings(String hubName) {
//        log.info("Applying mappings for {}: {}", getName(), hubName);
//        Collection<TfMapping> mappings = mappingRepository.findByHub(getName(), hubName);
//        Collection<TfMapping> staticMappings = mappings.stream().filter(TfMapping::isStaticMapping).collect(Collectors.toSet());
//        Optional<TfMapping> dynamicMappingOptional = mappings.stream().filter(m -> !m.isStaticMapping()).findAny();
//        Collection<Source> sources = sourceRepository.findByRepositoryAndHubLatest(getName(), hubName);
//        Collection<Standard> standards = new HashSet<>();
//        ScriptingEngine scriptingEngine = scriptingEngines.stream().filter(se -> properties.getScriptingLanguage().equals(se.getLanguage())).findAny().orElseThrow(RuntimeException::new);
//        try {
//            for (Source source : sources) {
//                Map<String, Object> rawMap = gson.fromJson(source.getContent(), Map.class);
//                Map<String, Object> standardMap = new HashMap<>();
//                if (dynamicMappingOptional.isPresent()) {
//                    TfMapping mapping = dynamicMappingOptional.get();
//                    standardMap = gson.fromJson(scriptingEngine.execute(mapping.getFrom(), source.getContent()), Map.class);
//                }
//                for (TfMapping mapping : staticMappings) {
//                    Collection<String> values;
//                    Dynamic dynamicValues = Dynamic.from(rawMap).get(mapping.getFrom(), properties.getLevelsSeparator());
//                    if (dynamicValues.isPresent()) {
//                        if (dynamicValues.isList()) {
//                            values = dynamicValues.asList();
//                        } else {
//                            values = Collections.singletonList(dynamicValues.asString());
//                        }
//                    } else {
//                        values = Collections.emptyList();
//                    }
//                    String[] path = mapping.getTo().split(properties.getLevelsSeparator());
//                    putValueByPath(standardMap, path, values);
//                }
//                Standard standard = new Standard();
//                standard.setId(source.getId());
//                standard.setContent(gson.toJson(standardMap));
//                standard.setRawVersion(source.getRawVersion());
//                standard.setCuratedVersion(source.getCuratedVersion());
//                standard.setStandardVersion(0L);
//                Optional<Standard> standardLatest =
//                        standardRepository.findByIdAndRawVersionAndCuratedVersionLatest(standard.getId(),
//                                standard.getRawVersion(),
//                                standard.getCuratedVersion());
//                if (standardLatest.isPresent()) {
//                    standard.setStandardVersion(standardLatest.get().getStandardVersion() + 1);
//                } else {
//                    standard.setStandardVersion(1L);
//                }
//                standards.add(standard);
//            }
//            standardRepository.saveAll(standards);
//            applicationEventPublisher.publishEvent(new DataReloadEvent(hubName, Operation.MAPPING));
//        } catch (Exception e) {
//            log.error(e.getMessage(), e);
//            return;
//        }
//        log.info("Success!");
    }

    @SuppressWarnings("unchecked")
    private void putValueByPath(Map<String, Object> standardMap, String[] path, Collection<String> values) {
        Map<String, Object> nestedMap = standardMap;
        for (int i = 0; i < path.length - 1; i++) {
            nestedMap = (Map<String, Object>) nestedMap.computeIfAbsent(path[i], k -> new HashMap<String, Object>());
        }
        if (CollectionUtils.size(values) == 1) {
            nestedMap.put(path[path.length - 1], values.iterator().next());
        } else {
            nestedMap.put(path[path.length - 1], values);
        }
    }

    @Autowired
    public void setProperties(TrackFindProperties properties) {
        this.properties = properties;
    }

    @Autowired
    public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Autowired
    public void setCacheService(CacheService cacheService) {
        this.cacheService = cacheService;
    }

    @Autowired
    public void setSearchService(SearchService searchService) {
        this.searchService = searchService;
    }

    @Autowired
    public void setJdbcTemplate(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Autowired
    public void setHubRepository(HubRepository hubRepository) {
        this.hubRepository = hubRepository;
    }

    @Autowired
    public void setObjectTypeRepository(ObjectTypeRepository objectTypeRepository) {
        this.objectTypeRepository = objectTypeRepository;
    }

    @Autowired
    public void setVersionRepository(VersionRepository versionRepository) {
        this.versionRepository = versionRepository;
    }

    @Autowired
    public void setObjectRepository(ObjectRepository objectRepository) {
        this.objectRepository = objectRepository;
    }

    @Autowired
    public void setMappingRepository(MappingRepository mappingRepository) {
        this.mappingRepository = mappingRepository;
    }

    @Autowired
    public void setExecutorService(ExecutorService workStealingPool) {
        this.executorService = workStealingPool;
    }

    @Autowired
    public void setGson(Gson gson) {
        this.gson = gson;
    }

    @Autowired
    public void setScriptingEngines(Collection<ScriptingEngine> scriptingEngines) {
        this.scriptingEngines = scriptingEngines;
    }

}
