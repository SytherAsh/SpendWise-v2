package com.spendwise.categorization;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Required test for E4-S3-T4 (invoked directly, not waiting for the real weekly schedule):
 * triggers a real call to {@link CategorizationService#triggerRetrain}; a failure there doesn't
 * crash the scheduler thread.
 */
class MlRetrainingJobTest {

    private final CategorizationService categorizationService = mock(CategorizationService.class);
    private final MlRetrainingJob job = new MlRetrainingJob(categorizationService);

    @Test
    void invokesTriggerRetrain() {
        job.run();

        verify(categorizationService).triggerRetrain();
    }

    @Test
    void retrainFailureDoesNotThrow() {
        doThrow(new RuntimeException("FastAPI unreachable")).when(categorizationService).triggerRetrain();

        assertThatCode(job::run).doesNotThrowAnyException();
    }
}
