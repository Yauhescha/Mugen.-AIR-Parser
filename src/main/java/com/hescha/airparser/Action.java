package com.hescha.airparser;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class Action {
    private int actionNumber;
    private List<ActionItem> actionItems = new ArrayList<>();
    private boolean hasLoop;
    private int loopStartIndex;
}
