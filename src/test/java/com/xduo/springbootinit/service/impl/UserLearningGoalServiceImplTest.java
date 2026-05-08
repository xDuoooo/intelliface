package com.xduo.springbootinit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xduo.springbootinit.model.entity.UserLearningGoal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserLearningGoalServiceImplTest {

    private UserLearningGoalServiceImpl userLearningGoalService;

    @BeforeEach
    void setUp() {
        userLearningGoalService = spy(new UserLearningGoalServiceImpl());
    }

    @Test
    void getOrInitByUserIdReturnsExistingGoal() {
        UserLearningGoal goal = new UserLearningGoal();
        goal.setUserId(1L);
        goal.setDailyTarget(6);
        doReturn(goal).when(userLearningGoalService).getOne(any());

        UserLearningGoal result = userLearningGoalService.getOrInitByUserId(1L);

        assertSame(goal, result);
    }

    @Test
    void getOrInitByUserIdCreatesDefaultGoalWhenMissing() {
        doReturn(null).when(userLearningGoalService).getOne(any());
        doReturn(true).when(userLearningGoalService).save(any(UserLearningGoal.class));

        UserLearningGoal result = userLearningGoalService.getOrInitByUserId(3L);

        assertEquals(3L, result.getUserId());
        assertEquals(3, result.getDailyTarget());
        assertEquals(1, result.getReminderEnabled());
        ArgumentCaptor<UserLearningGoal> captor = ArgumentCaptor.forClass(UserLearningGoal.class);
        verify(userLearningGoalService).save(captor.capture());
        assertEquals(3L, captor.getValue().getUserId());
        assertEquals(3, captor.getValue().getDailyTarget());
        assertEquals(1, captor.getValue().getReminderEnabled());
    }

    @Test
    void updateUserLearningGoalUpdatesDailyTargetAndReminderFlag() {
        UserLearningGoal goal = new UserLearningGoal();
        goal.setUserId(5L);
        goal.setDailyTarget(3);
        goal.setReminderEnabled(1);
        doReturn(goal).when(userLearningGoalService).getOrInitByUserId(5L);
        doReturn(true).when(userLearningGoalService).updateById(goal);

        boolean result = userLearningGoalService.updateUserLearningGoal(5L, 8, false);

        assertTrue(result);
        assertEquals(8, goal.getDailyTarget());
        assertEquals(0, goal.getReminderEnabled());
    }

    @Test
    void updateUserLearningGoalReturnsFalseWhenUpdateFails() {
        UserLearningGoal goal = new UserLearningGoal();
        goal.setUserId(6L);
        doReturn(goal).when(userLearningGoalService).getOrInitByUserId(6L);
        doReturn(false).when(userLearningGoalService).updateById(goal);

        boolean result = userLearningGoalService.updateUserLearningGoal(6L, 4, true);

        assertEquals(false, result);
        assertEquals(4, goal.getDailyTarget());
        assertEquals(1, goal.getReminderEnabled());
    }

    @Test
    void listReminderEnabledGoalsDelegatesToList() {
        java.util.List<UserLearningGoal> expected = java.util.List.of(new UserLearningGoal(), new UserLearningGoal());
        doReturn(expected).when(userLearningGoalService).list(any(QueryWrapper.class));

        java.util.List<UserLearningGoal> result = userLearningGoalService.listReminderEnabledGoals();

        assertSame(expected, result);
    }
}
