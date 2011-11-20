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

public class HelpHandler implements CommandLineHandler {

    private static final String[] ARG_NAMES = new String[]{"/H", "/?", "-H", "-?"};

    public String getDescription() {
        return "print help about commands";
    }

    public boolean processArgument(final String argument, final PreprocessorContext configurator) {
        boolean result = false;
        if (argument != null && !argument.isEmpty()) {
            
            final String argUpperCase = argument.trim().toUpperCase();
            
            for (final String str : ARG_NAMES) {
                if (str.equals(argUpperCase)) {
                    result = true;
                    break;
                }
            }
        }
        return result;
    }

    public String getKeyName() {
        final StringBuilder result = new StringBuilder();
        for (int li = 0; li < ARG_NAMES.length; li++) {
            if (li > 0) {
                result.append(',');
            }
            result.append(ARG_NAMES[li]);
        }
        return result.toString();
    }
}
