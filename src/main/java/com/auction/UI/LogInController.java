package com.auction.UI;

import java.io.IOException;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;

public class LogInController {
    @FXML
    private AnchorPane LoginPane;
    @FXML
    private PasswordField passwordField;

    @FXML
    private TextField usernameField;

    @FXML 
    private Label errorLabel;

    @FXML
    private void SwitchToHome() throws IOException {
        LoginPane.getStyleClass();
        if(true) {//Xử lý tài khoản mật khẩu
        ViewManager.SwitchView("Home");
        } else{
            errorLabel.setText("Incorrect username or password!");
        }
    }
    @FXML 
    private void SwitchToRegister() throws IOException {
        ViewManager.SwitchView("Register");
    }
}
