package com.auction.UI;

import java.io.IOException;

import javafx.fxml.FXML;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;

public class LogInController {
    @FXML
    private PasswordField passwordField;

    @FXML
    private Button loginButton;

    @FXML
    private TextField usernameField;

    private String username;

    @FXML
    public void SwitchToHome() throws IOException {
        ViewManager.SwitchView("Home");
    }
}
