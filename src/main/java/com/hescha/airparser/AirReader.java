package com.hescha.airparser;

import lombok.Data;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class AirReader {

    @Data
    class Action {
        int actionNumber;
        List<ActionItem> actionItems = new ArrayList<>();
        boolean hasLoop;
        int loopStartIndex;
    }

    @Data
    class ActionItem {
        List<Clsn> clsn1List = new ArrayList<>();
        List<Clsn> clsn2List = new ArrayList<>();
        int spriteX, spriteY, offsetX, offsetY, time;
        String flip;
        String colorChanging;
        Double xScale, yScale;
        Integer rotateAngle;
        boolean isFilled;
    }

    @Data
    class Clsn {
        int x1, y1, x2, y2;

        public Clsn(int x1, int y1, int x2, int y2) {
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
        }
    }

    public static void main(String[] args) throws IOException {
        AirReader m = new AirReader();
        m.meth();
    }

    private void meth() throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader("D:\\ProgramFiles\\Java\\idea-workspace\\AirParser\\AirParser\\src\\main\\resources\\test.txt"));
        String line;
        Action action = null;
        List<Action> actions = new ArrayList<>();
        ActionItem actionItem = null;
        int rows = 0;
        while ((line = reader.readLine()) != null) {
            rows++;
            System.out.println("rows: " + rows+ " " + line);
            if (line.isEmpty() || line.startsWith(";")) {
                continue;
            } else if (line.startsWith("[Begin Action")) {
                action = new Action();
                action.setActionNumber(Integer.parseInt(line.substring(14, line.length() - 1)));
                actions.add(action);
                System.out.println("action number: " + action.actionNumber);
            } else if (line.startsWith("Clsn2: ") || line.startsWith("Clsn2Default: ") || line.startsWith("Clsn1: ")) {
                boolean isClsn2 = line.startsWith("Clsn2: ");
                actionItem = new ActionItem();
                action.getActionItems().add(actionItem);
                int beginIndex = line.startsWith("Clsn2Default: ") ? 14 : 7;
                int count = Integer.parseInt(line.substring(beginIndex));
                for (int i = 0; i < count; i++) {
                    line = reader.readLine();
                    String[] parts = line.substring(line.indexOf('=')+1).split(",");
                    Clsn clsn = new Clsn(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()),
                            Integer.parseInt(parts[2].trim()), Integer.parseInt(parts[3].trim()));
                    if (isClsn2) {
                        actionItem.getClsn2List().add(clsn);
                    } else {
                        actionItem.getClsn1List().add(clsn);
                    }
                }
            } else if (line.startsWith("Loopstart")) {
                action.hasLoop = true;
                action.loopStartIndex = action.getActionItems().size() - 1;
            } else {
                if(actionItem.isFilled){
                    actionItem = new ActionItem();
                    action.getActionItems().add(actionItem);
                }
                String[] parts = line.split(",");
                actionItem.setSpriteX(Integer.parseInt(parts[0].trim()));
                actionItem.setSpriteY(Integer.parseInt(parts[1].trim()));
                actionItem.setOffsetX(Integer.parseInt(parts[2].trim()));
                actionItem.setOffsetY(Integer.parseInt(parts[3].trim()));
                actionItem.setTime(Integer.parseInt(parts[4].trim()));

                if(parts.length>=6){
                    actionItem.setFlip(parts[5]);
                }
                if(parts.length>=7){
                    actionItem.setColorChanging(parts[6]);
                }
                if(parts.length>=8){
                    actionItem.setXScale(Double.parseDouble(parts[7]));
                }
                if(parts.length>=9){
                    actionItem.setYScale(Double.parseDouble(parts[8]));
                }
                if(parts.length>=10){
                    actionItem.setRotateAngle(Integer.parseInt(parts[9]));
                }

                actionItem.isFilled = true;
            }
        }

        System.out.println("1111111");
    }
}

