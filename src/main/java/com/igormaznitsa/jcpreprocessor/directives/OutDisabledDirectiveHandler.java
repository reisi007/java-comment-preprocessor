package com.igormaznitsa.jcpreprocessor.directives;

import com.igormaznitsa.jcpreprocessor.containers.PreprocessingState;
import com.igormaznitsa.jcpreprocessor.containers.PreprocessingFlag;
import com.igormaznitsa.jcpreprocessor.context.PreprocessorContext;

public class OutDisabledDirectiveHandler  extends AbstractDirectiveHandler {

    @Override
    public String getName() {
        return "-";
    }

    @Override
    public boolean hasExpression() {
        return false;
    }

    @Override
    public String getReference() {
        return null;
    }

    @Override
    public AfterProcessingBehaviour execute(final String string, final PreprocessingState state, final PreprocessorContext configurator) {
        state.getPreprocessingFlags().add(PreprocessingFlag.TEXT_OUTPUT_DISABLED);
        return AfterProcessingBehaviour.PROCESSED;
    }
    
    
}
