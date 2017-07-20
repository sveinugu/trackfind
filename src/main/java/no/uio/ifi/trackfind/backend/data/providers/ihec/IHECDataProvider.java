package no.uio.ifi.trackfind.backend.data.providers.ihec;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import no.uio.ifi.trackfind.backend.data.providers.AbstractDataProvider;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Collection;
import java.util.Date;
import java.util.Map;

@Slf4j
@Component
public class IHECDataProvider extends AbstractDataProvider {

    private static final String RELEASES_URL = "http://epigenomesportal.ca//cgi-bin/api/getReleases.py";
    private static final String FETCH_URL = "http://epigenomesportal.ca/cgi-bin/api/getDataHub.py?data_release_id=";
    private static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";
    private static final String DATASETS = "datasets";
    private static final String HUB_DESCRIPTION = "hub_description";
    private static final String SAMPLES = "samples";

    @SuppressWarnings("unchecked")
    @Override
    public Collection<Map> fetchData() throws IOException {
        Gson gson = new GsonBuilder().setDateFormat(DATE_FORMAT).create();
        Release lastRelease;
        try (InputStreamReader reader = new InputStreamReader(new URL(RELEASES_URL).openStream())) {
            Collection<Release> releases = gson.fromJson(reader, new TypeToken<Collection<Release>>() {
            }.getType());
            lastRelease = releases.stream().sorted().findFirst().orElseThrow(RuntimeException::new);
        }
        Integer lastReleaseId = lastRelease.getId();
        try (InputStreamReader reader = new InputStreamReader(new URL(FETCH_URL + lastReleaseId).openStream())) {
            Map grid = gson.fromJson(reader, Map.class);
            Map datasetsMap = (Map) grid.get(DATASETS);
            Collection<Map> datasets = datasetsMap.values();
            Map samplesMap = (Map) grid.get(SAMPLES);
            Object hubDescription = grid.get(HUB_DESCRIPTION);
            for (Map<String, Object> dataset : datasets) {
                String sampleId = String.valueOf(dataset.get("sample_id"));
                Object sample = samplesMap.get(sampleId);
                dataset.put("sample_data", sample);
                dataset.put("hub_description", hubDescription);
            }
            return datasets;
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public String getUrlFromDataset(Map dataset) {
        Map browser = (Map) dataset.get("browser");
        Collection<Map> signals = (Collection<Map>) browser.get("signal");
        Map signal = signals.iterator().next();
        return String.valueOf(signal.get("big_data_url"));
    }

    @Data
    private class Release implements Comparable<Release> {

        @SerializedName("assembly")
        private String assembly;

        @SerializedName("id")
        private Integer id;

        @SerializedName("integration_date")
        private Date integrationDate;

        @SerializedName("publishing_group")
        private String publishingGroup;

        @SerializedName("release_policies_url")
        private String releasePoliciesUrl;

        @SerializedName("releasing_group")
        private String releasingGroup;

        @SerializedName("species")
        private String species;

        @SerializedName("taxon_id")
        private Integer taxonId;

        @Override
        public int compareTo(Release that) {
            return this.getIntegrationDate().compareTo(that.getIntegrationDate());
        }

    }


}
