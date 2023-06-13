package com.hescha.airparser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class AirReader {
    private static final String COMMA = ",";
    private static final char EQUALS = '=';
    private static final String CLSN_2_DEFAULT = "Clsn2Default: ";
    private static final String CLSN_2 = "Clsn2: ";
    private static final String BEGIN_ACTION = "[Begin Action";
    private static final String LOOPSTART = "Loopstart";
    private static final String CLSN_1 = "Clsn1: ";

    // Example
//    public static void main(String[] args) throws IOException {
//        AirReader reader = new AirReader();
//        reader.read("src/main/resources/test.air");
//    }

    public List<Action> read(String pathToFile) throws IOException {
        return read(new File(pathToFile));
    }

    public List<Action> read(File file) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(file));
        String line;
        Action action = null;
        List<Action> actions = new ArrayList<>();
        ActionItem actionItem = null;

        while ((line = reader.readLine()) != null) {
            if (line.isEmpty() || line.startsWith(";")) {
                continue;
            } else if (line.startsWith(BEGIN_ACTION)) {
                action = readAction(line, actions);
            } else if (line.startsWith(CLSN_2) || line.startsWith(CLSN_2_DEFAULT) || line.startsWith(CLSN_1)) {
                actionItem = readClnsAsNewActionItem(reader, line, action);
            } else if (line.startsWith(LOOPSTART)) {
                setLoopForAction(action);
            } else {
                actionItem = fillActionItem(line, action, actionItem);
            }
        }

        return actions;
    }

    private ActionItem fillActionItem(String line, Action action, ActionItem actionItem) {
        if (actionItem.isFilled()) {
            actionItem = new ActionItem();
            action.getActionItems().add(actionItem);
        }
        String[] parts = line.split(COMMA);
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

        actionItem.setFilled(true);
        return actionItem;
    }

    private void setLoopForAction(Action action) {
        action.setHasLoop(true);
        action.setLoopStartIndex(action.getActionItems().size() - 1);
    }

    private ActionItem readClnsAsNewActionItem(BufferedReader reader, String line, Action action) throws IOException {
        ActionItem actionItem;
        boolean isClsn2 = line.startsWith(CLSN_2);
        actionItem = new ActionItem();
        action.getActionItems().add(actionItem);
        int beginIndex = line.startsWith(CLSN_2_DEFAULT) ? 14 : 7;
        int count = Integer.parseInt(line.substring(beginIndex));
        for (int i = 0; i < count; i++) {
            line = reader.readLine();
            String[] parts = line.substring(line.indexOf(EQUALS) + 1).split(COMMA);
            Clsn clsn = new Clsn(
                    Integer.parseInt(parts[0].trim()),
                    Integer.parseInt(parts[1].trim()),
                    Integer.parseInt(parts[2].trim()),
                    Integer.parseInt(parts[3].trim()));
            if (isClsn2) {
                actionItem.getClsn2List().add(clsn);
            } else {
                actionItem.getClsn1List().add(clsn);
            }
        }
        return actionItem;
    }

    private Action readAction(String line, List<Action> actions) {
        int actionNumber = Integer.parseInt(line.substring(14, line.length() - 1));
        Action action = new Action();
        action.setActionNumber(actionNumber);
        actions.add(action);
        return action;
    }
}

