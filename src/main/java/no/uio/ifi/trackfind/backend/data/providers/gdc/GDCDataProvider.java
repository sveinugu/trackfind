package no.uio.ifi.trackfind.backend.data.providers.gdc;

import lombok.extern.slf4j.Slf4j;
import no.uio.ifi.trackfind.backend.data.providers.PaginationAwareDataProvider;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

/**
 * Fetches data from <a href="https://docs.gdc.cancer.gov/">GDC</a>.
 *
 * @author Dmytro Titov
 */
@Slf4j
//@Component
public class GDCDataProvider extends PaginationAwareDataProvider {

    private static final String CASES = "https://api.gdc.cancer.gov/cases?" +
            "filters={%20%22op%22:%22and%22,%20%22content%22:[%20{%20%22op%22:%22=%22,%20%22content%22:{%20%22field%22:%22files.access%22,%20%22value%22:%22open%22%20}%20}%20]%20}&";
    private static final String CASES_EXPANDED = CASES + "expand=demographic,samples,samples.annotations,samples.portions,samples.portions.center,samples.portions.analytes,samples.portions.analytes.aliquots,samples.portions.analytes.aliquots.annotations,samples.portions.analytes.aliquots.center,samples.portions.analytes.annotations,samples.portions.slides,samples.portions.slides.annotations,samples.portions.annotations,annotations,exposures,files,files.index_files,files.downstream_analyses,files.downstream_analyses.output_files,files.archive,files.metadata_files,files.center,files.analysis,files.analysis.input_files,files.analysis.metadata,files.analysis.metadata.read_groups,files.analysis.metadata.read_groups.read_group_qcs,family_histories,diagnoses,diagnoses.treatments,project,project.program,tissue_source_site&";
    private static final String DOWNLOAD = "https://api.gdc.cancer.gov/data/";
    private static final String FILES = "files";

    @Override
    protected long getEntriesPerPage() {
        return 10;
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    @Override
    protected void fetchData(String hubName) throws Exception {
        log.info("Fetching cases...");
        fetchPages(hubName, CASES, CASES_EXPANDED, GDCPage.class);
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    @Override
    protected void postProcessPage(Collection<Map> page) {
        page.forEach(dataset -> dataset.put(FILES, ((Collection<Map<String, Object>>) dataset.get(FILES)).parallelStream().filter(f -> "open".equals(f.get("access"))).collect(Collectors.toSet())));
    }

    @Autowired
    @Override
    public void setExecutorService(ExecutorService singleThreadExecutor) {
        this.executorService = singleThreadExecutor;
    }

}
