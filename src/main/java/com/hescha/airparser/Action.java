package com.hescha.airparser;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class Action {
    int actionNumber;
    List<ActionItem> actionItems = new ArrayList<>();
    boolean hasLoop;
    int loopStartIndex;
}
