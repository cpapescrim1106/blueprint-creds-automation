package com.blueprint.relay;

import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

public class AutoLoginService {
    private static final AtomicBoolean loggedIn = new AtomicBoolean(false);

    public static void start() {
        String user = System.getProperty("oms.username");
        String pass = System.getProperty("oms.password");

        if (user == null || pass == null) {
            Agent.log("AutoLoginService: oms.username or oms.password not set. Skipping auto-login.");
            return;
        }

        Agent.log("AutoLoginService: Started monitoring for Login window...");

        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                while (!loggedIn.get()) {
                    try {
                        Thread.sleep(1000);
                        scanWindows(user, pass);
                    } catch (InterruptedException e) {
                        break;
                    } catch (Exception e) {
                        Agent.log("AutoLoginService error: " + e.toString());
                    }
                }
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private static void scanWindows(final String user, final String pass) {
        final Window[] windows = Window.getWindows();
        if (windows == null) return;

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                for (Window w : windows) {
                    if (!w.isVisible()) continue;
                    
                    String title = getTitle(w);
                    if (title != null && title.toLowerCase().contains("login")) {
                        attemptLogin(w, user, pass);
                    }
                }
            }
        });
    }

    private static String getTitle(Window w) {
        if (w instanceof JDialog) return ((JDialog) w).getTitle();
        if (w instanceof JFrame) return ((JFrame) w).getTitle();
        return null;
    }

    private static void attemptLogin(Window w, String user, String pass) {
        if (loggedIn.get()) return;

        Agent.log("AutoLoginService: Found Login Window: " + w);
        dumpHierarchy(w, "");

        JTextField userField = findComponent(w, JTextField.class, null);
        JPasswordField passField = findComponent(w, JPasswordField.class, null);
        JButton loginButton = findComponent(w, JButton.class, "Login");
        
        if (loginButton == null) {
             loginButton = findComponent(w, JButton.class, "Log in");
        }
        
        if (loginButton == null) {
             loginButton = findComponent(w, JButton.class, "OK");
        }

        if (userField != null && passField != null && loginButton != null) {
            Agent.log("AutoLoginService: Injecting credentials...");
            userField.setText(user);
            passField.setText(pass);
            
            Agent.log("AutoLoginService: Clicking Login button...");
            loggedIn.set(true);
            loginButton.doClick();
        } else {
            Agent.log("AutoLoginService: Could not find all components (User: %s, Pass: %s, Btn: %s)",
                    userField != null, passField != null, loginButton != null);
        }
    }

    private static void dumpHierarchy(Component c, String indent) {
        String text = "";
        if (c instanceof JButton) text = " [text=" + ((JButton) c).getText() + "]";
        if (c instanceof JTextField) text = " [text=" + ((JTextField) c).getText() + "]";
        
        Agent.log(indent + c.getClass().getName() + text);
        
        if (c instanceof Container) {
            for (Component child : ((Container) c).getComponents()) {
                dumpHierarchy(child, indent + "  ");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Component> T findComponent(Container container, Class<T> type, String textData) {
        for (Component c : container.getComponents()) {
            if (type.isInstance(c)) {
                if (c instanceof JButton && textData != null) {
                    String text = ((JButton) c).getText();
                    if (text != null && text.toLowerCase().contains(textData.toLowerCase())) {
                        return (T) c;
                    }
                } else if (textData == null) {
                     return (T) c;
                }
            }
            if (c instanceof Container) {
                T found = findComponent((Container) c, type, textData);
                if (found != null) return found;
            }
        }
        return null;
    }
}
