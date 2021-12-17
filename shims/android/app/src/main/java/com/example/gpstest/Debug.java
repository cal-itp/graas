package com.example.gpstest;

import android.widget.EditText;

public class Debug {
    public static final boolean DEBUG = true;
    public static EditText textField;

    public static synchronized void log(Object msg) {
        if (DEBUG) {
            System.out.println(msg);
            textField.append(msg.toString() + '\n');
        }
    }

    public static synchronized void error(Object msg) {
        System.out.println(msg);
        textField.append("*** " + msg.toString() + '\n');
    }

    public static void bail() {
        System.err.println("Debug.bail()");
        System.exit(0);
    }
}