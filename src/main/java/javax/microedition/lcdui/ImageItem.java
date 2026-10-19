// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package javax.microedition.lcdui;
public class ImageItem extends Item {
    private Image image; private String alt; private int mode;
    public ImageItem(String label, Image img, int layout, String altText) { this(label, img, layout, altText, PLAIN); }
    public ImageItem(String label, Image img, int layout, String altText, int appearanceMode) {
        super(label); image = img; alt = altText; mode = appearanceMode; setLayout(layout);
    }
    public Image getImage() { return image; }
    public void setImage(Image i) { image = i; changed(); }
    public String getAltText() { return alt; }
    public void setAltText(String t) { alt = t; changed(); }
    public int getAppearanceMode() { return mode; }
}
