package com.lulan.shincolle.ai;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public final class GoalSelectorHelper {

    private GoalSelectorHelper() {
    }

    public static void stopAndClear(GoalSelector selector) {
        stopAndRemove(selector, goal -> true);
    }

    public static void stopAndRemove(GoalSelector selector, Predicate<Goal> predicate) {
        List<WrappedGoal> goals = new ArrayList<>(selector.getAvailableGoals());
        goals.stream()
                .filter(goal -> predicate.test(goal.getGoal()))
                .filter(WrappedGoal::isRunning)
                .forEach(WrappedGoal::stop);
        selector.removeAllGoals(predicate);
    }
}
