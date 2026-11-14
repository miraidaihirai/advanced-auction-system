package com.auction.ui;

import java.io.IOException;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;

public class RegisterController {
    @FXML
    private AnchorPane RegisterPane;

    @FXML
    private TextField username;

    @FXML
    private PasswordField password;

    @FXML
    private Label errorRegister;

    @FXML
    private void Register()throws IOException{
        RegisterPane.getStyleClass();
        if(true){ //Nếu đky được tài khoản
            ViewManager.SwitchView("LogIn");
        }else{
            errorRegister.setText("Username or password already exists!");
        }
    }
    
}
