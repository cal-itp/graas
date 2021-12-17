package gtfu;

import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

public interface UIEventListener {
    public void keyPressed(KeyEvent e);
    public void keyReleased(KeyEvent e);
    public void mousePressed(MouseEvent e);
    public void mouseReleased(MouseEvent e);
    public void mouseDragged(MouseEvent e);
}