package com.hescha.airparser;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AirReader {

    public static final String PATH_TO_FILE = "D:\\ProgramFiles\\Java\\idea-workspace\\AirParser\\AirParser\\src\\main\\resources\\test.txt";

    public static void main(String[] args) throws IOException {
        read();
    }

    private static void read() throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(PATH_TO_FILE));
        String line;
        Action action = null;
        List<Action> actions = new ArrayList<>();
        ActionItem actionItem = null;
        int rows = 0;
        while ((line = reader.readLine()) != null) {
            rows++;
            System.out.println("rows: " + rows + " " + line);
            if (line.isEmpty() || line.startsWith(";")) {
                continue;
            } else if (line.startsWith("[Begin Action")) {
                action = readAction(line, actions);
            } else if (line.startsWith("Clsn2: ") || line.startsWith("Clsn2Default: ") || line.startsWith("Clsn1: ")) {
                actionItem = readClnsAsNewActionItem(reader, line, action);
            } else if (line.startsWith("Loopstart")) {
                setLoopForAction(action);
            } else {
                actionItem = fillActionItem(line, action, actionItem);
            }
        }

        System.out.println(Arrays.toString(actions.toArray()));
    }

    private static ActionItem fillActionItem(String line, Action action, ActionItem actionItem) {
        if (actionItem.isFilled) {
            actionItem = new ActionItem();
            action.getActionItems().add(actionItem);
        }
        String[] parts = line.split(",");
        actionItem.setSpriteX(Integer.parseInt(parts[0].trim()));
        actionItem.setSpriteY(Integer.parseInt(parts[1].trim()));
        actionItem.setOffsetX(Integer.parseInt(parts[2].trim()));
        actionItem.setOffsetY(Integer.parseInt(parts[3].trim()));
        actionItem.setTime(Integer.parseInt(parts[4].trim()));

        if (parts.length >= 6) {
            actionItem.setFlip(parts[5]);
        }
        if (parts.length >= 7) {
            actionItem.setColorChanging(parts[6]);
        }
        if (parts.length >= 8) {
            actionItem.setXScale(Double.parseDouble(parts[7]));
        }
        if (parts.length >= 9) {
            actionItem.setYScale(Double.parseDouble(parts[8]));
        }
        if (parts.length >= 10) {
            actionItem.setRotateAngle(Integer.parseInt(parts[9]));
        }

        actionItem.isFilled = true;
        return actionItem;
    }

    private static void setLoopForAction(Action action) {
        action.hasLoop = true;
        action.loopStartIndex = action.getActionItems().size() - 1;
    }

    private static ActionItem readClnsAsNewActionItem(BufferedReader reader, String line, Action action) throws IOException {
        ActionItem actionItem;
        boolean isClsn2 = line.startsWith("Clsn2: ");
        actionItem = new ActionItem();
        action.getActionItems().add(actionItem);
        int beginIndex = line.startsWith("Clsn2Default: ") ? 14 : 7;
        int count = Integer.parseInt(line.substring(beginIndex));
        for (int i = 0; i < count; i++) {
            line = reader.readLine();
            String[] parts = line.substring(line.indexOf('=') + 1).split(",");
            Clsn clsn = new Clsn(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()),
                    Integer.parseInt(parts[2].trim()), Integer.parseInt(parts[3].trim()));
            if (isClsn2) {
                actionItem.getClsn2List().add(clsn);
            } else {
                actionItem.getClsn1List().add(clsn);
            }
        }
        return actionItem;
    }

    private static Action readAction(String line, List<Action> actions) {
        Action action;
        action = new Action();
        action.setActionNumber(Integer.parseInt(line.substring(14, line.length() - 1)));
        actions.add(action);
        System.out.println("action number: " + action.actionNumber);
        return action;
    }
}

