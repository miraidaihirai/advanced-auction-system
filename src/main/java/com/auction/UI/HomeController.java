package com.auction.ui;

import javafx.fxml.FXML;
import java.io.IOException;

public class HomeController {
    @FXML
    public void SwitchToLogin() throws IOException {
        ViewManager.SwitchView("LogIn");
    }

}
