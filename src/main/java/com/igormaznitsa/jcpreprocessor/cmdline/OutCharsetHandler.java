/*
 * Copyright 2011 Igor Maznitsa (http://www.igormaznitsa.com)
 *
 * This library is free software; you can redistribute it and/or modify
 * it under the terms of version 3 of the GNU Lesser General Public
 * License as published by the Free Software Foundation.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the
 * Free Software Foundation, Inc., 59 Temple Place, Suite 330,
 * Boston, MA 02111-1307  USA
 */
package com.igormaznitsa.jcpreprocessor.cmdline;

import com.igormaznitsa.jcpreprocessor.context.PreprocessorContext;
import com.igormaznitsa.jcpreprocessor.utils.PreprocessorUtils;

public class OutCharsetHandler implements CommandLineHandler {

    private static final String ARG_NAME = "/TT:";

    public String getKeyName() {
        return ARG_NAME;
    }

    public String getDescription() {
        return "set the output charset for processing text files, default value is " + PreprocessorContext.DEFAULT_CHARSET;
    }

    public boolean processArgument(final String argument, final PreprocessorContext configurator) {

        boolean result = false;

        if (argument != null) {
            if (argument.toUpperCase().startsWith(ARG_NAME)) {
                final String value = PreprocessorUtils.extractTrimmedTail(ARG_NAME, argument);

                if (!value.isEmpty()) {
                    configurator.setInCharacterEncoding(value);
                    result = true;
                }
            }
        }
        return result;
    }
}
