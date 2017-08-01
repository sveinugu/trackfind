package no.uio.ifi.trackfind.backend.rest.controllers;

import no.uio.ifi.trackfind.backend.data.providers.DataProvider;
import no.uio.ifi.trackfind.backend.services.TrackFindService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Main REST controller exposing all main features of the system.
 *
 * @author Dmytro Titov
 */
@RestController
public class TrackFindController {

    private final TrackFindService trackFindService;

    @Autowired
    public TrackFindController(TrackFindService trackFindService) {
        this.trackFindService = trackFindService;
    }

    /**
     * Gets all available DataProviders.
     *
     * @return List of DataProviders available.
     * @throws Exception In case of some error.
     */
    @GetMapping(path = "providers", produces = "application/json")
    public Object getProviders() throws Exception {
        return trackFindService.getDataProviders().stream().map(DataProvider::getName).collect(Collectors.toSet());
    }

    /**
     * Performs reinitialization of particular DataProvider.
     *
     * @param provider DataProvider name.
     * @throws Exception In case of some error.
     */
    @GetMapping(path = "/{provider}/reinit", produces = "application/json")
    public void reinit(@PathVariable String provider) throws Exception {
        trackFindService.getDataProvider(provider).updateIndex();
    }

    /**
     * Performs reinitialization of all providers.
     *
     * @throws Exception In case of some error.
     */
    @GetMapping(path = "/reinit", produces = "application/json")
    public void reinit() throws Exception {
        trackFindService.getDataProviders().forEach(DataProvider::updateIndex);
    }

    /**
     * Gets DataProvider's metamodel in tree form.
     *
     * @param provider DataProvider name.
     * @return Metamodel in tree form.
     * @throws Exception In case of some error.
     */
    @GetMapping(path = "/{provider}/metamodel-tree", produces = "application/json")
    public Object getMetamodelTree(@PathVariable String provider) throws Exception {
        return trackFindService.getDataProvider(provider).getMetamodelTree();
    }

    /**
     * Gets DataProvider's metamodel in flat form.
     *
     * @param provider DataProvider name.
     * @return Metamodel in flat form.
     * @throws Exception In case of some error.
     */
    @GetMapping(path = "/{provider}/metamodel-flat", produces = "application/json")
    public Object getMetamodelFlat(@PathVariable String provider) throws Exception {
        return trackFindService.getDataProvider(provider).getMetamodelFlat().asMap();
    }

    /**
     * Gets the list of attributes available in DataProvider's metamodel.
     *
     * @param provider   DataProvider name.
     * @param expression Mask to filter attributes (by 'contains' rule).
     * @return List of attributes.
     * @throws Exception In case of some error.
     */
    @GetMapping(path = "/{provider}/attributes", produces = "application/json")
    public Object getAttributes(@PathVariable String provider,
                                @RequestParam(required = false, defaultValue = "") String expression) throws Exception {
        Set<String> attributes = trackFindService.getDataProvider(provider).getMetamodelFlat().asMap().keySet();
        return attributes.stream().filter(a -> a.contains(expression)).collect(Collectors.toSet());
    }

    /**
     * Gets the list of values available in for a particular attribute of DataProvider's metamodel.
     *
     * @param provider   DataProvider name.
     * @param attribute  Attribute name.
     * @param expression Mask to filter values (by 'contains' rule).
     * @return List of values.
     * @throws Exception In case of some error.
     */
    @GetMapping(path = "/{provider}/values", produces = "application/json")
    public Object getValues(@PathVariable String provider,
                            @RequestParam String attribute,
                            @RequestParam(required = false, defaultValue = "") String expression) throws Exception {
        Collection<String> values = trackFindService.getDataProvider(provider).getMetamodelFlat().get(attribute);
        return values.stream().filter(a -> a.contains(expression)).collect(Collectors.toSet());
    }

    /**
     * Performs search over the Directory of specified DataProvider.
     *
     * @param provider DataProvider name.
     * @param query    Search query (Lucene syntax, see https://lucene.apache.org/solr/guide/6_6/the-standard-query-parser.html).
     * @return Search results.
     * @throws Exception In case of some error.
     */
    @GetMapping(path = "/{provider}/search", produces = "application/json")
    public Object search(@PathVariable String provider,
                         @RequestParam String query) throws Exception {
        return trackFindService.getDataProvider(provider).search(query);
    }

}
