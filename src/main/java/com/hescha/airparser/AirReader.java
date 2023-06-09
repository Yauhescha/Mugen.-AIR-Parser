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
        List<Clsn2> clsn2List = new ArrayList<>();
        int spriteX, spriteY, offsetX, offsetY, time;
    }

    @Data
    class Clsn2 {
        int x1, y1, x2, y2;

        public Clsn2(int x1, int y1, int x2, int y2) {
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
        while ((line = reader.readLine()) != null) {
            if (line.isEmpty() || line.startsWith(";")) {
                continue;
            } else if (line.startsWith("[Begin Action")) {
                action = new Action();
                action.setActionNumber(Integer.parseInt(line.substring(14, line.length() - 1)));
                actions.add(action);
            } else if (line.startsWith("Clsn2: ")) {
                actionItem = new ActionItem();
                action.getActionItems().add(actionItem);
                int count = Integer.parseInt(line.substring(7));
                for (int i = 0; i < count; i++) {
                    line = reader.readLine();
                    String[] parts = line.substring(13).split(",");
                    Clsn2 clsn2 = new Clsn2(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()),
                            Integer.parseInt(parts[2].trim()), Integer.parseInt(parts[3].trim()));
                    actionItem.getClsn2List().add(clsn2);
                }
            } else if(line.startsWith("Loopstart")){
                action.hasLoop=true;
                action.loopStartIndex = action.getActionItems().size()-1;
            }
            else  {
                String[] parts = line.split(",");
                actionItem.setSpriteX(Integer.parseInt(parts[0].trim()));
                actionItem.setSpriteY(Integer.parseInt(parts[1].trim()));
                actionItem.setOffsetX(Integer.parseInt(parts[2].trim()));
                actionItem.setOffsetY(Integer.parseInt(parts[3].trim()));
                actionItem.setTime(Integer.parseInt(parts[4].trim()));
            }
        }

        System.out.println("1111111");
    }
}

