package com.auction.ui;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;

/**
 * Thư viện vector icon SVG sắc nét ở mọi độ phân giải cho BidX (không bị lỗi ô vuông emoji trên Windows).
 */
public final class SvgIcons {

    private SvgIcons() {
    }

    public static final String HOME = "M3 10.5L12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1v-9.5z";
    public static final String COMPASS = "M12 2a10 10 0 1 0 10 10A10 10 0 0 0 12 2zm3.8 6.2l-2.1 5.5-5.5 2.1 2.1-5.5 5.5-2.1z";
    public static final String GAVEL = "M14.5 2.5l7 7-2.8 2.8-7-7 2.8-2.8zM9.2 7.8l7 7-2.8 2.8-7-7 2.8-2.8zM2 20h12v2H2v-2zm7.5-7.5l2 2-6.5 6.5-2-2 6.5-6.5z";
    public static final String HISTORY = "M12 4a8 8 0 1 0 7.75 10h-2.1A6 6 0 1 1 12 6a5.9 5.9 0 0 1 4.22 1.78L13 11h7V4l-2.35 2.35A7.96 7.96 0 0 0 12 4zm-1 4v5l4.2 2.5.8-1.3-3.5-2.1V8h-1.5z";
    public static final String HEART = "M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z";
    public static final String WALLET = "M20 6H5a2 2 0 0 0-2 2v10a2 2 0 0 0 2 2h15a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2zm0 12H5V8h15v10zm-4-4.5a1.5 1.5 0 1 0 1.5-1.5 1.5 1.5 0 0 0-1.5 1.5zM4 4h14v1.5H4V4z";
    public static final String BELL = "M12 22a2 2 0 0 0 2-2h-4a2 2 0 0 0 2 2zm6-6v-5a6 6 0 0 0-4.5-5.8V4.5a1.5 1.5 0 0 0-3 0v.7A6 6 0 0 0 6 11v5l-2 2v1h16v-1l-2-2z";
    public static final String PLUS = "M11 4h2v7h7v2h-7v7h-2v-7H4v-2h7V4z";
    public static final String SHIELD = "M12 2L4 5v6.09c0 5.05 3.41 9.76 8 10.91 4.59-1.15 8-5.86 8-10.91V5l-8-3zm-1 14l-3.5-3.5 1.4-1.4L11 13.2l4.6-4.6 1.4 1.4L11 16z";
    public static final String USERS = "M16 11a3 3 0 1 0-3-3 3 3 0 0 0 3 3zm-8 0a3 3 0 1 0-3-3 3 3 0 0 0 3 3zm0 2c-2.67 0-8 1.34-8 4v2h10v-2c0-1.5.75-2.78 2-3.6A10.2 10.2 0 0 0 8 13zm8 0c-.34 0-.7.02-1.08.07 1.34.96 2.08 2.24 2.08 3.93v2h7v-2c0-2.66-5.33-4-8-4z";
    public static final String CHECK = "M9 16.2L4.8 12l-1.4 1.4L9 19 21 7l-1.4-1.4L9 16.2z";
    public static final String CLOSE = "M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12 19 6.41z";
    public static final String SEARCH = "M15.5 14h-.79l-.28-.27A6.5 6.5 0 1 0 14 15.5l.27.28v.79l5 5 1.5-1.5-5-5zm-6 0a4.5 4.5 0 1 1 4.5-4.5 4.5 4.5 0 0 1-4.5 4.5z";
    public static final String REFRESH = "M17.65 6.35A7.95 7.95 0 0 0 12 4a8 8 0 1 0 7.73 10h-2.08A6 6 0 1 1 12 6a5.9 5.9 0 0 1 4.22 1.78L13 11h7V4l-2.35 2.35z";
    public static final String LOGOUT = "M17 7l-1.41 1.41L18.17 11H8v2h10.17l-2.58 2.58L17 17l5-5-5-5zM4 5h8V3H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h8v-2H4V5z";
    public static final String ARROW_LEFT = "M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z";
    public static final String ZAP = "M13 2L3 14h7l-1 8 10-12h-7l1-8z";
    public static final String CLOCK = "M12 2a10 10 0 1 0 10 10A10 10 0 0 0 12 2zm1 10.4l3.6 2.1-.8 1.3L11 13V7h2v5.4z";
    public static final String LOCK = "M18 8h-1V6a5 5 0 0 0-10 0v2H6a2 2 0 0 0-2 2v10a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V10a2 2 0 0 0-2-2zm-6 9a2 2 0 1 1 2-2 2 2 0 0 1-2 2zm3.1-9H8.9V6a3.1 3.1 0 0 1 6.2 0v2z";
    public static final String TROPHY = "M19 5h-2V3H7v2H5a2 2 0 0 0-2 2v1a5 5 0 0 0 4.39 4.96A5 5 0 0 0 11 15.9V19H7v2h10v-2h-4v-3.1a5 5 0 0 0 3.61-2.94A5 5 0 0 0 21 8V7a2 2 0 0 0-2-2zM5 8V7h2v3.82A3 3 0 0 1 5 8zm14 0a3 3 0 0 1-2 2.82V7h2v1z";
    public static final String FLAME = "M13.5 2s.5 3-2 5.5C9.5 9.5 8 11 8 14a4 4 0 0 0 8 0c0-2-1-3.5-1-3.5s2.5 1 2.5 4.5A5.5 5.5 0 0 1 12 21a5.5 5.5 0 0 1-5.5-5.5C6.5 10.5 11 7 13.5 2z";
    public static final String STORE = "M4 4h16l1 5H3l1-5zm1 7h14v9h-4v-6H9v6H5v-9z";
    public static final String TRENDING = "M16 6l2.29 2.29-4.88 4.88-4-4L2 16.59 3.41 18l6-6 4 4 6.3-6.29L22 12V6h-6z";
    public static final String SUN = "M12 7a5 5 0 1 0 5 5 5 5 0 0 0-5-5zm0-5h1.5v3h-3V2zm0 17h1.5v3h-3v-3zM2 11h3v2H2zm17 0h3v2h-3zM4.22 5.64l1.42-1.42 2.12 2.12-1.42 1.42zm12.02 12.02l1.42-1.42 2.12 2.12-1.42 1.42zm0-12.02l2.12-2.12 1.42 1.42-2.12 2.12zM4.22 18.36l2.12-2.12 1.42 1.42-2.12 2.12z";
    public static final String MOON = "M12 3a9 9 0 1 0 9 9c0-.46-.04-.92-.1-1.36a5.389 5.389 0 0 1-4.4 2.26 5.403 5.403 0 0 1-3.14-9.8c-.44-.06-.9-.1-1.36-.1z";
    public static final String ARROW_DOWN = "M11 4h2v12.17l5.59-5.58L20 12l-8 8-8-8 1.41-1.41L11 16.17V4z";
    public static final String ARROW_UP = "M13 20h-2V7.83l-5.59 5.58L4 12l8-8 8 8-1.41 1.41L13 7.83V20z";
    public static final String BANK = "M12 2L2 7v2h20V7L12 2zm-7 9v7H3v2h18v-2h-2v-7h-2v7h-3v-7h-4v7H7v-7H5z";

    public static Node create(String pathData, double size, String hexColor) {
        SVGPath svg = new SVGPath();
        svg.setContent(pathData);
        svg.setFill(Color.web(hexColor));
        double scale = size / 24.0;
        svg.setScaleX(scale);
        svg.setScaleY(scale);
        Group wrapper = new Group(svg);
        wrapper.setPickOnBounds(false);
        return wrapper;
    }
}
