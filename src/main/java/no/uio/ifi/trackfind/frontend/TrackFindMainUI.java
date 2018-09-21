package no.uio.ifi.trackfind.frontend;

import com.vaadin.annotations.Theme;
import com.vaadin.annotations.Title;
import com.vaadin.annotations.Widgetset;
import com.vaadin.data.HasValue;
import com.vaadin.data.TreeData;
import com.vaadin.data.ValueProvider;
import com.vaadin.data.provider.TreeDataProvider;
import com.vaadin.event.ShortcutAction;
import com.vaadin.event.ShortcutListener;
import com.vaadin.server.*;
import com.vaadin.shared.data.sort.SortDirection;
import com.vaadin.shared.ui.ContentMode;
import com.vaadin.shared.ui.ValueChangeMode;
import com.vaadin.shared.ui.dnd.DropEffect;
import com.vaadin.shared.ui.dnd.EffectAllowed;
import com.vaadin.spring.annotation.SpringUI;
import com.vaadin.ui.*;
import com.vaadin.ui.components.grid.TreeGridDragSource;
import com.vaadin.ui.dnd.DropTargetExtension;
import lombok.extern.slf4j.Slf4j;
import no.uio.ifi.trackfind.backend.converters.DatasetsToGSuiteConverter;
import no.uio.ifi.trackfind.backend.dao.Dataset;
import no.uio.ifi.trackfind.backend.data.providers.DataProvider;
import no.uio.ifi.trackfind.frontend.components.KeyboardInterceptorExtension;
import no.uio.ifi.trackfind.frontend.components.TrackFindTree;
import no.uio.ifi.trackfind.frontend.data.TreeNode;
import no.uio.ifi.trackfind.frontend.filters.MainTreeFilter;
import no.uio.ifi.trackfind.frontend.listeners.AddToQueryButtonClickListener;
import no.uio.ifi.trackfind.frontend.listeners.TextAreaDropListener;
import no.uio.ifi.trackfind.frontend.listeners.TreeItemClickListener;
import no.uio.ifi.trackfind.frontend.listeners.TreeSelectionListener;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;

/**
 * Main Vaadin UI of the application.
 * Capable of displaying metadata for repositories, constructing and executing search queries along with exporting the results.
 * Uses custom theme (VAADIN/themes/trackfind/trackfind.scss).
 * Uses custom WidgetSet (TrackFindWidgetSet.gwt.xml).
 *
 * @author Dmytro Titov
 */
@SpringUI(path = "/")
@Widgetset("TrackFindWidgetSet")
@Title("TrackFind")
@Theme("trackfind")
@Slf4j
public class TrackFindMainUI extends AbstractUI {

    private DatasetsToGSuiteConverter datasetsToGSuiteConverter;

    private int numberOfResults;

    private TextArea queryTextArea;
    private TextField limitTextField;
    private TextArea resultsTextArea;
    private FileDownloader gSuiteFileDownloader;
    private FileDownloader jsonFileDownloader;
    private Button exportGSuiteButton;
    private Button exportJSONButton;

    @Override
    protected void init(VaadinRequest vaadinRequest) {
        HorizontalLayout headerLayout = buildHeaderLayout();
        VerticalLayout treeLayout = buildTreeLayout();
        VerticalLayout queryLayout = buildQueryLayout();
        VerticalLayout resultsLayout = buildResultsLayout();
        HorizontalLayout mainLayout = buildMainLayout(treeLayout, queryLayout, resultsLayout);
        HorizontalLayout footerLayout = buildFooterLayout();
        VerticalLayout outerLayout = buildOuterLayout(headerLayout, mainLayout, footerLayout);
        setContent(outerLayout);
    }

    protected VerticalLayout buildTreeLayout() {
        tabSheet = new TabSheet();
        tabSheet.setSizeFull();

        for (DataProvider dataProvider : trackFindService.getDataProviders()) {
            TrackFindTree<TreeNode> tree = buildTree(dataProvider);
            refreshTrees(false);
            tabSheet.addTab(tree, dataProvider.getName());
        }

        Panel treePanel = new Panel("Model browser", tabSheet);
        treePanel.setSizeFull();

        CheckBox checkBox = new CheckBox("Advanced metamodel");
        checkBox.addValueChangeListener((HasValue.ValueChangeListener<Boolean>) event -> refreshTrees(event.getValue()));

        TextField attributesFilterTextField = new TextField("Filter attributes", (HasValue.ValueChangeListener<String>) event -> {
            TreeDataProvider<TreeNode> dataProvider = getCurrentTreeDataProvider();
            ((MainTreeFilter) dataProvider.getFilter()).setAttributesFilter(event.getValue());
            dataProvider.refreshAll();
        });
        attributesFilterTextField.setValueChangeMode(ValueChangeMode.EAGER);
        attributesFilterTextField.setWidth(100, Sizeable.Unit.PERCENTAGE);

        TextField valuesFilterTextField = new TextField("Filter values", (HasValue.ValueChangeListener<String>) event -> {
            TreeDataProvider<TreeNode> dataProvider = getCurrentTreeDataProvider();
            ((MainTreeFilter) dataProvider.getFilter()).setValuesFilter(event.getValue());
            dataProvider.refreshAll();
        });
        valuesFilterTextField.setValueChangeMode(ValueChangeMode.EAGER);
        valuesFilterTextField.setWidth(100, Sizeable.Unit.PERCENTAGE);

        Button addToQueryButton = new Button("Add to query ➚", new AddToQueryButtonClickListener(this));
        addToQueryButton.setWidth(100, Unit.PERCENTAGE);

        VerticalLayout treeLayout = new VerticalLayout(treePanel, checkBox, attributesFilterTextField, valuesFilterTextField, addToQueryButton);
        treeLayout.setSizeFull();
        treeLayout.setExpandRatio(treePanel, 1f);
        return treeLayout;
    }

    @SuppressWarnings("unchecked")
    protected TrackFindTree<TreeNode> buildTree(DataProvider dataProvider) {
        TreeDataProvider treeDataProvider = new TreeDataProvider(new TreeData<>());
        treeDataProvider.setFilter(new MainTreeFilter(treeDataProvider, "", ""));
        treeDataProvider.setSortOrder((ValueProvider<TreeNode, String>) TreeNode::toString, SortDirection.ASCENDING);
        dataProviders.put(treeDataProvider, dataProvider);

        TrackFindTree<TreeNode> tree = new TrackFindTree<>(dataProvider);
        tree.setSelectionMode(Grid.SelectionMode.MULTI);
        tree.addItemClickListener(new TreeItemClickListener(tree));
        tree.addSelectionListener(new TreeSelectionListener(tree, new KeyboardInterceptorExtension(tree)));
        tree.setDataProvider(treeDataProvider);
        tree.setSizeFull();
        tree.setStyleGenerator((StyleGenerator<TreeNode>) item -> item.isValue() ? "value-tree-node" : null);

        TreeGridDragSource<TreeNode> dragSource = new TreeGridDragSource<>((TreeGrid<TreeNode>) tree.getCompositionRoot());
        dragSource.setEffectAllowed(EffectAllowed.COPY);

        return tree;
    }

    private HorizontalLayout buildMainLayout(VerticalLayout treeLayout, VerticalLayout queryLayout, VerticalLayout resultsLayout) {
        HorizontalLayout mainLayout = new HorizontalLayout(treeLayout, queryLayout, resultsLayout);
        mainLayout.setSizeFull();
        return mainLayout;
    }

    private VerticalLayout buildResultsLayout() {
        resultsTextArea = new TextArea();
        resultsTextArea.setSizeFull();
        resultsTextArea.setReadOnly(true);
        resultsTextArea.addStyleName("scrollable-text-area");
        resultsTextArea.addValueChangeListener((HasValue.ValueChangeListener<String>) event -> {
            if (StringUtils.isEmpty(event.getValue())) {
                exportGSuiteButton.setEnabled(false);
                exportGSuiteButton.setCaption("Export as GSuite file");
                exportJSONButton.setEnabled(false);
                exportJSONButton.setCaption("Export as JSON file");
            } else {
                exportGSuiteButton.setEnabled(true);
                exportGSuiteButton.setCaption("Export (" + numberOfResults + ") entries as GSuite file");
                exportJSONButton.setEnabled(true);
                exportJSONButton.setCaption("Export (" + numberOfResults + ") entries as JSON file");
            }
        });
        Panel resultsPanel = new Panel("Data", resultsTextArea);
        resultsPanel.setSizeFull();
        exportGSuiteButton = new Button("Export as GSuite file");
        exportGSuiteButton.setEnabled(false);
        exportGSuiteButton.setWidth(100, Unit.PERCENTAGE);
        exportJSONButton = new Button("Export as JSON file");
        exportJSONButton.setEnabled(false);
        exportJSONButton.setWidth(100, Unit.PERCENTAGE);
        VerticalLayout resultsLayout = new VerticalLayout(resultsPanel, exportGSuiteButton, exportJSONButton);
        resultsLayout.setSizeFull();
        resultsLayout.setExpandRatio(resultsPanel, 1f);
        return resultsLayout;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private VerticalLayout buildQueryLayout() {
        queryTextArea = new TextArea();
        queryTextArea.setSizeFull();
        queryTextArea.addShortcutListener(new ShortcutListener("Execute query", ShortcutAction.KeyCode.ENTER, new int[]{ShortcutAction.ModifierKey.CTRL}) {
            @Override
            public void handleAction(Object sender, Object target) {
                executeQuery(queryTextArea.getValue());
            }
        });
        queryTextArea.addShortcutListener(new ShortcutListener("Execute query", ShortcutAction.KeyCode.ENTER, new int[]{ShortcutAction.ModifierKey.META}) {
            @Override
            public void handleAction(Object sender, Object target) {
                executeQuery(queryTextArea.getValue());
            }
        });
        Button searchButton = new Button("Search", (Button.ClickListener) clickEvent -> executeQuery(queryTextArea.getValue()));
        queryTextArea.addValueChangeListener((HasValue.ValueChangeListener<String>) event -> {
            searchButton.setEnabled(StringUtils.isNotEmpty(queryTextArea.getValue()));
        });
        queryTextArea.addStyleName("scrollable-text-area");
        DropTargetExtension<TextArea> dropTarget = new DropTargetExtension<>(queryTextArea);
        dropTarget.setDropEffect(DropEffect.COPY);
        dropTarget.addDropListener(new TextAreaDropListener(queryTextArea));
        Panel queryPanel = new Panel("Search query", queryTextArea);
        queryPanel.setSizeFull();

        searchButton.setWidth(100, Unit.PERCENTAGE);
        searchButton.setEnabled(false);

        limitTextField = new TextField("Limit", "10");
        limitTextField.setWidth(100, Unit.PERCENTAGE);
        limitTextField.setValueChangeMode(ValueChangeMode.EAGER);
        limitTextField.addValueChangeListener((HasValue.ValueChangeListener<String>) valueChangeEvent -> {
            String value = valueChangeEvent.getValue();
            value = StringUtils.isEmpty(value) ? "0" : value;
            try {
                Integer.parseInt(value);
                limitTextField.setComponentError(null);
                queryTextArea.setEnabled(true);
                searchButton.setEnabled(true);
            } catch (NumberFormatException e) {
                limitTextField.setComponentError(new UserError("Should be a valid integer number only!"));
                queryTextArea.setEnabled(false);
                searchButton.setEnabled(false);
            }
        });

        VerticalLayout helpLayout = buildHelpLayout();

        PopupView popup = new PopupView("Help", helpLayout);
        VerticalLayout queryLayout = new VerticalLayout(queryPanel, limitTextField, searchButton, popup);
        queryLayout.setSizeFull();
        queryLayout.setExpandRatio(queryPanel, 1f);
        return queryLayout;
    }

    private VerticalLayout buildHelpLayout() {
        Collection<Component> instructions = new ArrayList<>();
        instructions.add(new Label("<b>How to perform a search:<b> ", ContentMode.HTML));
        instructions.add(new Label("1. Navigate through metamodel tree using browser on the left."));
        instructions.add(new Label("2. Filter attributes or values using text-fields in the bottom if needed."));
        instructions.add(new Label("3. Select attribute or value(s) you want to use in the search query. Multiple values can be selected using <i>Shift</i> or <i>Ctrl</i> / <i>Command</i>.", ContentMode.HTML));
        instructions.add(new Label("4. Drag and drop attribute name or value to the query area or simply use <i>Add to query</i> button."));
        instructions.add(new Label("5. Correct query manually if necessary (some special characters should be escaped using backslash)."));
        instructions.add(new Label("6. Press <i>Ctrl+Shift</i> or <i>Command+Shift</i> or click <i>Search</i> button to execute the query.", ContentMode.HTML));
        instructions.add(new Label("<b>Hotkeys:<b> ", ContentMode.HTML));
        instructions.add(new Label("Use <i>Ctrl</i> or <i>Command</i> to select multiple values in tree.", ContentMode.HTML));
        instructions.add(new Label("Use <i>Shift</i> to select range of values in tree.", ContentMode.HTML));
        instructions.add(new Label("Hold <i>Alt</i> or <i>Option</i> key while dragging to use OR operator instead of AND.", ContentMode.HTML));
        instructions.add(new Label("Hold <i>Shift</i> key while dragging to add NOT operator.", ContentMode.HTML));
        return new VerticalLayout(instructions.toArray(new Component[]{}));
    }

    @SuppressWarnings("unchecked")
    private void executeQuery(String query) {
        DataProvider currentDataProvider = getCurrentDataProvider();

        String limit = limitTextField.getValue();
        limit = StringUtils.isEmpty(limit) ? "0" : limit;
        Collection<Dataset> results = currentDataProvider.search(query, Integer.parseInt(limit));
        if (results.isEmpty()) {
            resultsTextArea.setValue("");
            Notification.show("Nothing found for such request");
            return;
        }
        numberOfResults = results.size();
        StringBuilder jsonResult = new StringBuilder();
        for (Dataset dataset : results) {
            jsonResult.append(dataset.getRawDataset()).append("\n");
        }
        resultsTextArea.setValue(jsonResult.toString());

        String gSuiteResult = datasetsToGSuiteConverter.apply(results);

        if (gSuiteFileDownloader != null) {
            exportGSuiteButton.removeExtension(gSuiteFileDownloader);
        }
        if (jsonFileDownloader != null) {
            exportJSONButton.removeExtension(jsonFileDownloader);
        }
        Resource gSuiteResource = new StreamResource((StreamResource.StreamSource) () -> new ByteArrayInputStream(gSuiteResult.getBytes(Charset.defaultCharset())),
                Calendar.getInstance().getTime().toString() + ".gsuite");
        gSuiteFileDownloader = new FileDownloader(gSuiteResource);
        gSuiteFileDownloader.extend(exportGSuiteButton);

        Resource jsonResource = new StreamResource((StreamResource.StreamSource) () -> new ByteArrayInputStream(jsonResult.toString().getBytes(Charset.defaultCharset())),
                Calendar.getInstance().getTime().toString() + ".json");
        jsonFileDownloader = new FileDownloader(jsonResource);
        jsonFileDownloader.extend(exportJSONButton);
    }

    public TextArea getQueryTextArea() {
        return queryTextArea;
    }

    @Autowired
    public void setDatasetsToGSuiteConverter(DatasetsToGSuiteConverter datasetsToGSuiteConverter) {
        this.datasetsToGSuiteConverter = datasetsToGSuiteConverter;
    }

}
