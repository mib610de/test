package com.example.application.views.masterdetail;

import java.util.Optional;

import com.example.application.data.entity.SampleFoodProduct;
import com.example.application.data.service.SampleFoodProductService;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasStyle;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.splitlayout.SplitLayout;
import com.vaadin.flow.data.binder.BeanValidationBinder;
import com.vaadin.flow.data.binder.ValidationException;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.spring.data.VaadinSpringDataHelpers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.PageTitle;
import com.example.application.views.MainLayout;
import javax.annotation.security.PermitAll;
import java.nio.charset.StandardCharsets;
import com.vaadin.flow.component.html.Label;
import elemental.json.Json;
import com.vaadin.flow.component.textfield.TextField;
import java.io.ByteArrayOutputStream;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.data.renderer.TemplateRenderer;
import org.springframework.web.util.UriUtils;
import com.vaadin.flow.component.html.Image;
import java.util.Base64;

@PageTitle("Master-Detail")
@Route(value = "master-detail/:sampleFoodProductID?/:action?(edit)", layout = MainLayout.class)
@PermitAll
public class MasterDetailView extends Div implements BeforeEnterObserver {

    private final String SAMPLEFOODPRODUCT_ID = "sampleFoodProductID";
    private final String SAMPLEFOODPRODUCT_EDIT_ROUTE_TEMPLATE = "master-detail/%d/edit";

    private Grid<SampleFoodProduct> grid = new Grid<>(SampleFoodProduct.class, false);

    private Upload image;
    private Image imagePreview;
    private TextField name;
    private TextField eanCode;

    private Button cancel = new Button("Cancel");
    private Button save = new Button("Save");

    private BeanValidationBinder<SampleFoodProduct> binder;

    private SampleFoodProduct sampleFoodProduct;

    private SampleFoodProductService sampleFoodProductService;

    public MasterDetailView(@Autowired SampleFoodProductService sampleFoodProductService) {
        this.sampleFoodProductService = sampleFoodProductService;
        addClassNames("master-detail-view", "flex", "flex-col", "h-full");

        // Create UI
        SplitLayout splitLayout = new SplitLayout();
        splitLayout.setSizeFull();

        createGridLayout(splitLayout);
        createEditorLayout(splitLayout);

        add(splitLayout);

        // Configure Grid
        TemplateRenderer<SampleFoodProduct> imageRenderer = TemplateRenderer.<SampleFoodProduct>of(
                "<span style='border-radius: 50%; overflow: hidden; display: flex; align-items: center; justify-content: center; width: 64px; height: 64px'><img style='max-width: 100%' src='[[item.image]]' /></span>")
                .withProperty("image", SampleFoodProduct::getImage);
        grid.addColumn(imageRenderer).setHeader("Image").setWidth("96px").setFlexGrow(0);

        grid.addColumn("name").setAutoWidth(true);
        grid.addColumn("eanCode").setAutoWidth(true);
        grid.setItems(query -> sampleFoodProductService.list(
                PageRequest.of(query.getPage(), query.getPageSize(), VaadinSpringDataHelpers.toSpringDataSort(query)))
                .stream());
        grid.addThemeVariants(GridVariant.LUMO_NO_BORDER);
        grid.setHeightFull();

        // when a row is selected or deselected, populate form
        grid.asSingleSelect().addValueChangeListener(event -> {
            if (event.getValue() != null) {
                UI.getCurrent()
                        .navigate(String.format(SAMPLEFOODPRODUCT_EDIT_ROUTE_TEMPLATE, event.getValue().getId()));
            } else {
                clearForm();
                UI.getCurrent().navigate(MasterDetailView.class);
            }
        });

        // Configure Form
        binder = new BeanValidationBinder<>(SampleFoodProduct.class);

        // Bind fields. This where you'd define e.g. validation rules

        binder.bindInstanceFields(this);

        attachImageUpload(image, imagePreview);

        cancel.addClickListener(e -> {
            clearForm();
            refreshGrid();
        });

        save.addClickListener(e -> {
            try {
                if (this.sampleFoodProduct == null) {
                    this.sampleFoodProduct = new SampleFoodProduct();
                }
                binder.writeBean(this.sampleFoodProduct);
                this.sampleFoodProduct.setImage(imagePreview.getSrc());

                sampleFoodProductService.update(this.sampleFoodProduct);
                clearForm();
                refreshGrid();
                Notification.show("SampleFoodProduct details stored.");
                UI.getCurrent().navigate(MasterDetailView.class);
            } catch (ValidationException validationException) {
                Notification.show("An exception happened while trying to store the sampleFoodProduct details.");
            }
        });

    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        Optional<Integer> sampleFoodProductId = event.getRouteParameters().getInteger(SAMPLEFOODPRODUCT_ID);
        if (sampleFoodProductId.isPresent()) {
            Optional<SampleFoodProduct> sampleFoodProductFromBackend = sampleFoodProductService
                    .get(sampleFoodProductId.get());
            if (sampleFoodProductFromBackend.isPresent()) {
                populateForm(sampleFoodProductFromBackend.get());
            } else {
                Notification.show(String.format("The requested sampleFoodProduct was not found, ID = %d",
                        sampleFoodProductId.get()), 3000, Notification.Position.BOTTOM_START);
                // when a row is selected but the data is no longer available,
                // refresh grid
                refreshGrid();
                event.forwardTo(MasterDetailView.class);
            }
        }
    }

    private void createEditorLayout(SplitLayout splitLayout) {
        Div editorLayoutDiv = new Div();
        editorLayoutDiv.setClassName("flex flex-col");
        editorLayoutDiv.setWidth("400px");

        Div editorDiv = new Div();
        editorDiv.setClassName("p-l flex-grow");
        editorLayoutDiv.add(editorDiv);

        FormLayout formLayout = new FormLayout();
        Label imageLabel = new Label("Image");
        imagePreview = new Image();
        imagePreview.setWidth("100%");
        image = new Upload();
        image.getStyle().set("box-sizing", "border-box");
        image.getElement().appendChild(imagePreview.getElement());
        name = new TextField("Name");
        eanCode = new TextField("Ean Code");
        Component[] fields = new Component[]{imageLabel, image, name, eanCode};

        for (Component field : fields) {
            ((HasStyle) field).addClassName("full-width");
        }
        formLayout.add(fields);
        editorDiv.add(formLayout);
        createButtonLayout(editorLayoutDiv);

        splitLayout.addToSecondary(editorLayoutDiv);
    }

    private void createButtonLayout(Div editorLayoutDiv) {
        HorizontalLayout buttonLayout = new HorizontalLayout();
        buttonLayout.setClassName("w-full flex-wrap bg-contrast-5 py-s px-l");
        buttonLayout.setSpacing(true);
        cancel.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        buttonLayout.add(save, cancel);
        editorLayoutDiv.add(buttonLayout);
    }

    private void createGridLayout(SplitLayout splitLayout) {
        Div wrapper = new Div();
        wrapper.setId("grid-wrapper");
        wrapper.setWidthFull();
        splitLayout.addToPrimary(wrapper);
        wrapper.add(grid);
    }

    private void attachImageUpload(Upload upload, Image preview) {
        ByteArrayOutputStream uploadBuffer = new ByteArrayOutputStream();
        upload.setAcceptedFileTypes("image/*");
        upload.setReceiver((fileName, mimeType) -> {
            return uploadBuffer;
        });
        upload.addSucceededListener(e -> {
            String mimeType = e.getMIMEType();
            String base64ImageData = Base64.getEncoder().encodeToString(uploadBuffer.toByteArray());
            String dataUrl = "data:" + mimeType + ";base64,"
                    + UriUtils.encodeQuery(base64ImageData, StandardCharsets.UTF_8);
            upload.getElement().setPropertyJson("files", Json.createArray());
            preview.setSrc(dataUrl);
            uploadBuffer.reset();
        });
        preview.setVisible(false);
    }

    private void refreshGrid() {
        grid.select(null);
        grid.getLazyDataView().refreshAll();
    }

    private void clearForm() {
        populateForm(null);
    }

    private void populateForm(SampleFoodProduct value) {
        this.sampleFoodProduct = value;
        binder.readBean(this.sampleFoodProduct);
        this.imagePreview.setVisible(value != null);
        if (value == null) {
            this.imagePreview.setSrc("");
        } else {
            this.imagePreview.setSrc(value.getImage());
        }

    }
}
