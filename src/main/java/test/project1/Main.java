package test.project1;

import io.vertx.core.Launcher;

public class Main {
    public static void main(String[] args) {
        Launcher.executeCommand("run", Server.class.getName());
    }
}