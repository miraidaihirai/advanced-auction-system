package com.auction.UI;

import javafx.fxml.FXML;
import java.io.IOException;

public class HomeController {
    @FXML
    public void SwitchToLogin() throws IOException {
        ViewManager.SwitchView("LogIn");
    }

}
