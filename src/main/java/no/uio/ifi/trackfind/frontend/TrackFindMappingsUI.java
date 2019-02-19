package no.uio.ifi.trackfind.frontend;

import com.vaadin.annotations.Theme;
import com.vaadin.annotations.Title;
import com.vaadin.annotations.Widgetset;
import com.vaadin.data.HasValue;
import com.vaadin.event.selection.SelectionListener;
import com.vaadin.server.VaadinRequest;
import com.vaadin.spring.annotation.SpringUI;
import com.vaadin.ui.*;
import lombok.extern.slf4j.Slf4j;
import no.uio.ifi.trackfind.backend.dao.Hub;
import no.uio.ifi.trackfind.backend.dao.Mapping;
import no.uio.ifi.trackfind.backend.data.TreeNode;
import no.uio.ifi.trackfind.backend.data.providers.DataProvider;
import no.uio.ifi.trackfind.backend.repositories.HubRepository;
import no.uio.ifi.trackfind.backend.repositories.MappingRepository;
import no.uio.ifi.trackfind.frontend.components.TrackFindTree;
import no.uio.ifi.trackfind.frontend.filters.TreeFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.vaadin.aceeditor.AceEditor;
import org.vaadin.aceeditor.AceMode;
import org.vaadin.aceeditor.AceTheme;
import org.vaadin.dialogs.ConfirmDialog;

import javax.transaction.Transactional;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Mappings Vaadin UI of the application.
 * Uses custom theme (VAADIN/themes/trackfind/trackfind.scss).
 * Uses custom WidgetSet (TrackFindWidgetSet.gwt.xml).
 *
 * @author Dmytro Titov
 */
@SpringUI(path = "/mappings")
@Widgetset("TrackFindWidgetSet")
@Title("Mappings")
@Theme("trackfind")
@Slf4j
public class TrackFindMappingsUI extends AbstractUI {

    private MappingRepository mappingRepository;
    private HubRepository hubRepository;

    private Button addStaticMappingButton;
    private VerticalLayout attributesMappingLayout;

    private Map<ComboBox<String>, TextField> attributesStaticMapping = new HashMap<>();
    private AceEditor script;
    private TextField idAttribute;

    @Override
    protected void init(VaadinRequest vaadinRequest) {
        HorizontalLayout headerLayout = buildHeaderLayout();
        VerticalLayout treeLayout = buildTreeLayout();
        VerticalLayout attributesMappingOuterLayout = buildAttributesMappingLayout();
        HorizontalLayout mainLayout = buildMainLayout(treeLayout, attributesMappingOuterLayout);
        HorizontalLayout footerLayout = buildFooterLayout();
        VerticalLayout outerLayout = buildOuterLayout(headerLayout, mainLayout, footerLayout);
        setContent(outerLayout);
        loadConfiguration();
    }

    protected VerticalLayout buildTreeLayout() {
        tabSheet = new TabSheet();
        tabSheet.setSizeFull();

        for (Hub hub : trackFindService.getActiveTrackHubs()) {
            TrackFindTree<TreeNode> tree = buildTree(hub);
            tabSheet.addTab(tree, hub.getHub());
        }

        tabSheet.addSelectedTabChangeListener((TabSheet.SelectedTabChangeListener) event -> loadConfiguration());

        Panel treePanel = new Panel("Model browser", tabSheet);
        treePanel.setSizeFull();

        TextField attributesFilterTextField = createFilter(true);
        TextField valuesFilterTextField = createFilter(false);

        addStaticMappingButton = new Button("Add static mapping");
        addStaticMappingButton.setWidth(100, Unit.PERCENTAGE);
        addStaticMappingButton.setEnabled(!CollectionUtils.isEmpty(getCurrentTree().getSelectedItems()));
        addStaticMappingButton.addClickListener((Button.ClickListener) event -> {
            Set<TreeNode> selectedItems = getCurrentTree().getSelectedItems();
            String sourceAttribute = CollectionUtils.isEmpty(selectedItems) ? "" : selectedItems.iterator().next().getPath();
            addStaticMappingPair("", sourceAttribute);
        });

        HorizontalLayout mappingButtons = new HorizontalLayout(addStaticMappingButton);
        mappingButtons.setWidth(100, Unit.PERCENTAGE);
        VerticalLayout treeLayout = new VerticalLayout(treePanel, attributesFilterTextField, valuesFilterTextField, mappingButtons);
        treeLayout.setSizeFull();
        treeLayout.setExpandRatio(treePanel, 1f);
        return treeLayout;
    }

    @SuppressWarnings("unchecked")
    protected TrackFindTree<TreeNode> buildTree(Hub hub) {
        TrackFindTree<TreeNode> tree = new TrackFindTree<>(hub);
        tree.setDataProvider(trackFindDataProvider);
        tree.setSelectionMode(Grid.SelectionMode.SINGLE);
        tree.addSelectionListener((SelectionListener<TreeNode>) event -> addStaticMappingButton.setEnabled(!CollectionUtils.isEmpty(event.getAllSelectedItems()) && event.getFirstSelectedItem().get().isAttribute()));
        tree.setSizeFull();
        tree.setStyleGenerator((StyleGenerator<TreeNode>) item -> item.isAttribute() ? null : "value-tree-node");

        TreeGrid<TreeNode> treeGrid = (TreeGrid<TreeNode>) tree.getCompositionRoot();
        treeGrid.setFilter(new TreeFilter(hub, true, "", ""));

        return tree;
    }

    private HorizontalLayout buildMainLayout(VerticalLayout treeLayout, VerticalLayout attributesMappingOuterLayout) {
        HorizontalLayout mainLayout = new HorizontalLayout(treeLayout, attributesMappingOuterLayout);
        mainLayout.setExpandRatio(treeLayout, 0.33f);
        mainLayout.setExpandRatio(attributesMappingOuterLayout, 0.66f);
        mainLayout.setSizeFull();
        return mainLayout;
    }

    @SuppressWarnings("PMD.NPathComplexity")
    private VerticalLayout buildAttributesMappingLayout() {
        attributesMappingLayout = new VerticalLayout();
        attributesMappingLayout.setWidth(100, Unit.PERCENTAGE);

        script = new AceEditor();
        script.setSizeFull();
        script.setTheme(AceTheme.github);
        script.setMode(AceMode.coffee);

        TabSheet mappingsTabSheet = new TabSheet();
        mappingsTabSheet.setSizeFull();
        mappingsTabSheet.addTab(attributesMappingLayout, "Static");
        mappingsTabSheet.addTab(script, "Dynamic");

        mappingsTabSheet.addSelectedTabChangeListener((TabSheet.SelectedTabChangeListener) event -> {
            if (event.getTabSheet().getSelectedTab().equals(script)) {
                script.focus();
            }
        });

        Panel attributesMappingPanel = new Panel("Mappings", mappingsTabSheet);
        attributesMappingPanel.setSizeFull();
        Button saveButton = new Button("Save");
        saveButton.setSizeFull();
        saveButton.addClickListener((Button.ClickListener) event -> saveConfiguration());
        Button crawlButton = new Button("Crawl");
        crawlButton.setSizeFull();
        crawlButton.addClickListener((Button.ClickListener) event -> ConfirmDialog.show(getUI(),
                "Are you sure? " +
                        "Crawling is time-consuming process and will lead to changing the data in the database.",
                (ConfirmDialog.Listener) dialog -> {
                    if (dialog.isConfirmed()) {
                        Hub currentHub = getCurrentHub();
                        DataProvider dataProvider = trackFindService.getDataProvider(currentHub.getRepository());
                        dataProvider.crawlRemoteRepository(currentHub.getHub());
                        getCurrentTree().getDataProvider().refreshAll();
                    }
                }));
        Button applyMappingsButton = new Button("Apply mappings");
        applyMappingsButton.setSizeFull();
        applyMappingsButton.addClickListener((Button.ClickListener) event -> ConfirmDialog.show(getUI(),
                "Are you sure? " +
                        "Applying attribute mappings is time-consuming process and will lead to changing the data in the database.",
                (ConfirmDialog.Listener) dialog -> {
                    if (dialog.isConfirmed()) {
                        Hub currentHub = getCurrentHub();
                        DataProvider dataProvider = trackFindService.getDataProvider(currentHub.getRepository());
                        dataProvider.applyMappings(currentHub.getHub());
                    }
                }));
        idAttribute = new TextField("Internal ID attribute");
        idAttribute.setWidth(100, Unit.PERCENTAGE);
        HorizontalLayout buttonsLayout = new HorizontalLayout(saveButton, crawlButton, applyMappingsButton);
        buttonsLayout.setWidth(100, Unit.PERCENTAGE);
        buttonsLayout.setEnabled(!properties.isDemoMode());
        VerticalLayout attributesMappingOuterLayout = new VerticalLayout(attributesMappingPanel, idAttribute, buttonsLayout);
        attributesMappingOuterLayout.setSizeFull();
        attributesMappingOuterLayout.setExpandRatio(attributesMappingPanel, 0.8f);
        return attributesMappingOuterLayout;
    }

    private HorizontalLayout buildAttributeToAttributeLayout(String basicAttribute, String sourceAttribute) {
        HorizontalLayout attributeToAttributeLayout = new HorizontalLayout();
        attributeToAttributeLayout.setWidth(100, Unit.PERCENTAGE);

        TextField sourceAttributeTextField = new TextField("Source attribute name", sourceAttribute);
        sourceAttributeTextField.setWidth(100, Unit.PERCENTAGE);
        sourceAttributeTextField.setReadOnly(true);

        ComboBox<String> targetAttributeComboBox = buildBasicAttributesComboBox(basicAttribute);
        targetAttributeComboBox.setWidth(100, Unit.PERCENTAGE);
        attributesStaticMapping.put(targetAttributeComboBox, sourceAttributeTextField);

        Button deleteMappingButton = new Button("Delete");
        deleteMappingButton.setWidth(100, Unit.PERCENTAGE);
        deleteMappingButton.addClickListener((Button.ClickListener) event -> {
            ((AbstractLayout) attributeToAttributeLayout.getParent()).removeComponent(attributeToAttributeLayout);
            attributesStaticMapping.remove(targetAttributeComboBox);
        });

        attributeToAttributeLayout.addComponent(targetAttributeComboBox);
        attributeToAttributeLayout.setExpandRatio(targetAttributeComboBox, 0.4f);
        attributeToAttributeLayout.setComponentAlignment(targetAttributeComboBox, Alignment.BOTTOM_LEFT);
        attributeToAttributeLayout.addComponent(sourceAttributeTextField);
        attributeToAttributeLayout.setExpandRatio(sourceAttributeTextField, 0.4f);
        attributeToAttributeLayout.setComponentAlignment(sourceAttributeTextField, Alignment.BOTTOM_LEFT);
        attributeToAttributeLayout.addComponent(deleteMappingButton);
        attributeToAttributeLayout.setExpandRatio(deleteMappingButton, 0.2f);
        attributeToAttributeLayout.setComponentAlignment(deleteMappingButton, Alignment.BOTTOM_LEFT);
        return attributeToAttributeLayout;
    }

    private ComboBox<String> buildBasicAttributesComboBox(String targetAttribute) {
        ComboBox<String> targetAttributeName = new ComboBox<>("Target attribute name", schemaService.getAttributes());
        targetAttributeName.setSelectedItem(targetAttribute);
        return targetAttributeName;
    }

    private void loadConfiguration() {
        Hub currentHub = getCurrentHub();
        String repository = currentHub.getRepository();
        String hub = currentHub.getHub();
        Collection<Mapping> mappings = mappingRepository.findByRepositoryAndHub(repository, hub);
        Collection<Mapping> staticMappings = mappings.stream().filter(Mapping::isStaticMapping).collect(Collectors.toSet());
        attributesStaticMapping.clear();
        attributesMappingLayout.removeAllComponents();
        for (Mapping mapping : staticMappings) {
            addStaticMappingPair(mapping.getTo(), mapping.getFrom());
        }
        script.clear();
        mappings.stream().filter(m -> !m.isStaticMapping()).findAny().ifPresent(m -> script.setValue(m.getFrom()));
        idAttribute.setValue(Optional.ofNullable(currentHub.getIdAttribute()).orElse(""));
    }

    private void addStaticMappingPair(String basicAttribute, String sourceAttribute) {
        attributesMappingLayout.addComponent(buildAttributeToAttributeLayout(basicAttribute, sourceAttribute));
    }

    @Transactional
    protected void saveConfiguration() {
        Hub currentHub = getCurrentHub();
        String repository = currentHub.getRepository();
        String hub = currentHub.getHub();
        Collection<Mapping> existingMappings = mappingRepository.findByRepositoryAndHub(repository, hub);
        Collection<Mapping> mappings = new HashSet<>();
        mappingRepository.deleteInBatch(existingMappings);
        for (Map.Entry<ComboBox<String>, TextField> mappingPair : attributesStaticMapping.entrySet()) {
            Mapping mapping = new Mapping();
            mapping.setRepository(repository);
            mapping.setHub(hub);
            mapping.setStaticMapping(true);
            mapping.setFrom(mappingPair.getValue().getValue());
            mapping.setTo(mappingPair.getKey().getValue());
            mappings.add(mapping);
        }
        script.getOptionalValue().ifPresent(s -> {
            Mapping mapping = new Mapping();
            mapping.setRepository(repository);
            mapping.setHub(hub);
            mapping.setStaticMapping(false);
            mapping.setFrom(s);
            mappings.add(mapping);
        });
        mappingRepository.saveAll(mappings);
        currentHub.setIdAttribute(idAttribute.getValue());
        hubRepository.save(currentHub);
        Notification.show("Mappings saved. Press \"Apply\" for changes to take effect.");
    }

    @Autowired
    public void setMappingRepository(MappingRepository mappingRepository) {
        this.mappingRepository = mappingRepository;
    }

    @Autowired
    public void setHubRepository(HubRepository hubRepository) {
        this.hubRepository = hubRepository;
    }

}
