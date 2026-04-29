package controller.forum;

import entities.forum.ForumCategory;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import services.forum.ServiceForumCategory;
import util.AppNavigator;

import java.net.URL;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.ResourceBundle;

public class ForumAdminCategoriesController implements Initializable {

    @FXML private TableView<ForumCategory> categoryTable;
    @FXML private TableColumn<ForumCategory, String> colName;
    @FXML private TableColumn<ForumCategory, String> colDescription;
    @FXML private TableColumn<ForumCategory, String> colIcon;
    @FXML private TableColumn<ForumCategory, String> colPosition;
    @FXML private TableColumn<ForumCategory, String> colActive;

    @FXML private TextField nameField;
    @FXML private TextArea descriptionField;
    @FXML private TextField iconField;
    @FXML private ComboBox<String> emojiPicker;
    @FXML private TextField positionField;
    @FXML private CheckBox activeCheckBox;
    @FXML private Label formErrorLabel;
    @FXML private Button saveButton;
    @FXML private Button deleteButton;

    private final ServiceForumCategory categoryService = new ServiceForumCategory();
    private final ObservableList<ForumCategory> categories = FXCollections.observableArrayList();
    private final ObservableList<String> emojiChoices = FXCollections.observableArrayList(
            "", "💬", "📚", "🧠", "🧪", "💻", "📢", "❓", "🚀", "🎯", "🛠", "🧩"
    );
    private ForumCategory selectedCategory;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        colName.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getName()));
        colDescription.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getDescription() != null ? data.getValue().getDescription() : ""));
        colIcon.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getIcon() != null ? data.getValue().getIcon() : ""));
        colPosition.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().getPosition())));
        colActive.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getIsActive() == 1 ? "Yes" : "No"));

        categoryTable.setItems(categories);
        categoryTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            selectedCategory = newVal;
            populateForm(newVal);
        });

        emojiPicker.setItems(emojiChoices);
        iconField.textProperty().addListener((obs, oldValue, newValue) -> syncEmojiPicker(newValue));

        loadCategories();
        clearForm();
    }

    private void loadCategories() {
        categories.setAll(categoryService.getALL());
    }

    private void populateForm(ForumCategory cat) {
        if (cat == null) {
            clearForm();
            return;
        }
        nameField.setText(cat.getName());
        descriptionField.setText(cat.getDescription() != null ? cat.getDescription() : "");
        iconField.setText(cat.getIcon() != null ? cat.getIcon() : "");
        syncEmojiPicker(iconField.getText());
        positionField.setText(String.valueOf(cat.getPosition()));
        activeCheckBox.setSelected(cat.getIsActive() == 1);
        deleteButton.setDisable(false);
        saveButton.setText("Update");
    }

    private void clearForm() {
        selectedCategory = null;
        nameField.clear();
        descriptionField.clear();
        iconField.clear();
        emojiPicker.getSelectionModel().clearSelection();
        positionField.setText("0");
        activeCheckBox.setSelected(true);
        formErrorLabel.setText("");
        deleteButton.setDisable(true);
        saveButton.setText("Add");
        categoryTable.getSelectionModel().clearSelection();
    }

    private void syncEmojiPicker(String value) {
        if (value == null || value.trim().isEmpty()) {
            emojiPicker.getSelectionModel().clearSelection();
            return;
        }
        String icon = value.trim();
        if (emojiChoices.contains(icon)) {
            emojiPicker.getSelectionModel().select(icon);
        } else {
            emojiPicker.getSelectionModel().clearSelection();
        }
    }

    @FXML
    private void onEmojiSelected() {
        String selected = emojiPicker.getValue();
        if (selected == null) {
            return;
        }
        iconField.setText(selected.trim());
    }

    @FXML
    private void onSave() {
        formErrorLabel.setText("");
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            formErrorLabel.setText("Category name is required");
            return;
        }

        int position;
        try {
            position = Integer.parseInt(positionField.getText().trim());
        } catch (NumberFormatException e) {
            formErrorLabel.setText("Position must be a number");
            return;
        }

        if (selectedCategory == null) {
            // Add
            ForumCategory cat = new ForumCategory();
            cat.setName(name);
            cat.setDescription(descriptionField.getText().trim().isEmpty() ? null : descriptionField.getText().trim());
            cat.setIcon(iconField.getText().trim().isEmpty() ? null : iconField.getText().trim());
            cat.setPosition(position);
            cat.setIsActive((byte) (activeCheckBox.isSelected() ? 1 : 0));
            cat.setCreatedAt(new Timestamp(System.currentTimeMillis()));
            categoryService.add(cat);
        } else {
            // Update
            selectedCategory.setName(name);
            selectedCategory.setDescription(descriptionField.getText().trim().isEmpty() ? null : descriptionField.getText().trim());
            selectedCategory.setIcon(iconField.getText().trim().isEmpty() ? null : iconField.getText().trim());
            selectedCategory.setPosition(position);
            selectedCategory.setIsActive((byte) (activeCheckBox.isSelected() ? 1 : 0));
            categoryService.update(selectedCategory);
        }

        loadCategories();
        clearForm();
    }

    @FXML
    private void onDelete() {
        if (selectedCategory == null) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Delete category '" + selectedCategory.getName() + "' and all its topics?", ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Delete Category");
        confirm.setHeaderText(null);
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.YES) {
            categoryService.delete(selectedCategory);
            loadCategories();
            clearForm();
        }
    }

    @FXML
    private void onClear() {
        clearForm();
    }

    @FXML
    private void onBackToForum() {
        AppNavigator.showForum();
    }
}
