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
package com.igormaznitsa.jcp.expression;

import com.igormaznitsa.jcp.context.PreprocessorContext;
import com.igormaznitsa.jcp.expression.functions.AbstractFunction;
import com.igormaznitsa.jcp.expression.functions.FunctionDefinedByUser;
import com.igormaznitsa.jcp.expression.operators.AbstractOperator;
import com.igormaznitsa.jcp.expression.operators.OperatorSUB;
import com.igormaznitsa.jcp.extension.PreprocessorExtension;
import com.igormaznitsa.jcp.utils.PreprocessorUtils;
import java.io.IOException;
import java.io.PushbackReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * This class is a parser allows to parse an expression and make a tree as the output
 * 
 * @author Igor Maznitsa (igor.maznitsa@igormaznitsa.com)
 */
public final class ExpressionParser {

    /**
     * The enumeration describes inside states of the parses
     * 
     * @author Igor Maznitsa (igor.maznitsa@igormaznitsa.com)
     */
    private enum ParserState {

        WAIT,
        NUMBER,
        HEX_NUMBER,
        FLOAT_NUMBER,
        STRING,
        SPECIAL_CHAR,
        VALUE_OR_FUNCTION,
        OPERATOR
    }

    /**
     * The enumeration describes some special items which can be met in the expression
     * 
     * @author Igor Maznitsa (igor.maznitsa@igormaznitsa.com)
     */
    public enum SpecialItem implements ExpressionItem {

        BRACKET_OPENING('('),
        BRACKET_CLOSING(')'),
        COMMA(',');
        private final char chr;

        private SpecialItem(final char chr) {
            this.chr = chr;
        }

        public ExpressionItemPriority getExpressionItemPriority() {
            return null;
        }

        public ExpressionItemType getExpressionItemType() {
            return null;
        }
    }
    
    /**
     * It contains the instance for the parser, because the parser is a singletone
     */
    private static final ExpressionParser INSTANCE = new ExpressionParser();
    
    /**
     * The constant has been added to avoid repeating operations
     */
    private static final OperatorSUB OPERATOR_SUB = AbstractOperator.findForClass(OperatorSUB.class);

    public static ExpressionParser getInstance() {
        return INSTANCE;
    }

    /**
     * To parse an expression represented as a string and get a tree
     * @param expressionStr the expression string to be parsed, must not be null
     * @param context a preprocessor context to be used to get variable values, it can be null
     * @return a tree containing parsed expression
     * @throws IOException it will be thrown if there is a problem to read the expression string
     */
    public ExpressionTree parse(final String expressionStr, final PreprocessorContext context) throws IOException {
        if (expressionStr == null) {
            throw new NullPointerException("Expression is null");
        }
        
        final PushbackReader reader = new PushbackReader(new StringReader(expressionStr));
        final ExpressionTree result = new ExpressionTree();
        if (readExpression(reader, result, context, false, false) != null) {
            throw new IllegalStateException("Wrong result during expression parsing");
        }

        result.postProcess();

        return result;
    }

    /**
     * It reads an expression from a reader and fill a tree
     * @param reader the reader to be used as the character source, must not be null
     * @param tree the result tree to be filled by read items, must not be null
     * @param context a preprocessor context to be used for variables, it can be null
     * @param insideBracket the flag shows that the expression can be ended by a bracket
     * @param argument the flag shows that the expression can be ended by a comma
     * @return the last read expression item (a comma or a bracket for instance), it can be null
     * @throws IOException it will be thrown if there is a problem in reading from the reader
     */
    public ExpressionItem readExpression(final PushbackReader reader, final ExpressionTree tree, final PreprocessorContext context, final boolean insideBracket, final boolean argument) throws IOException {
        boolean working = true;

        ExpressionItem result = null;

        while (working) {
            final ExpressionItem nextItem = nextItem(reader, context);
            if (nextItem == null) {
                working = false;
                result = null;
            } else {

                if (nextItem.getExpressionItemType() == null) {
                    if (nextItem == SpecialItem.BRACKET_CLOSING) {
                        if (insideBracket) {
                            working = false;
                            result = nextItem;
                        } else {
                            if (argument) {
                                working = false;
                                result = nextItem;
                            } else {
                                throw new IllegalStateException("Closing bracket without any opening one detected");
                            }
                        }
                    } else if (nextItem == SpecialItem.BRACKET_OPENING) {
                        final ExpressionTree subExpression = new ExpressionTree();
                        if (SpecialItem.BRACKET_CLOSING != readExpression(reader, subExpression, context, true, false)) {
                            throw new IllegalStateException("Unclosed bracket detected");
                        }
                        tree.addTree(subExpression);
                    } else if (nextItem == SpecialItem.COMMA) {
                        return nextItem;
                    }
                } else {
                    if (nextItem.getExpressionItemType() == ExpressionItemType.FUNCTION) {
                        final AbstractFunction function = (AbstractFunction) nextItem;
                        ExpressionTree functionTree = readFunction(function, reader, context);
                        tree.addTree(functionTree);
                    } else {
                        tree.addItem(nextItem);
                    }
                }
            }
        }
        return result;
    }

    /**
     * The auxiliary method allows to form a function and its arguments as a tree
     * @param function the function which arguments will be read from the stream, must not be null
     * @param reader the reader to be used as the character source, must not be null
     * @param context a preprocessor context, it will be used for a user functions and variables, it can be null
     * @return an expression tree containing parsed function arguments
     * @throws IOException it will be thrown if there is any problem to read chars
     */
    ExpressionTree readFunction(final AbstractFunction function, final PushbackReader reader, final PreprocessorContext context) throws IOException {
        final ExpressionItem expectedBracket = nextItem(reader, context);
        if (expectedBracket == null) {
            throw new IllegalStateException("A function without parameters detected [" + function.getName() + ']');
        }

        final int arity = function.getArity();

        ExpressionTree functionTree = null;

        if (arity == 0) {
            final ExpressionTree subExpression = new ExpressionTree();
            final ExpressionItem lastItem = readFunctionArgument(reader, subExpression, context);
            if (SpecialItem.BRACKET_CLOSING != lastItem) {
                throw new IllegalArgumentException("There is not closing bracket for function [" + function.getName() + ']');
            } else if (subExpression.getRoot() != null) {
                throw new IllegalStateException("The function \'" + function.getName() + "\' doesn't need arguments");
            } else {
                functionTree = new ExpressionTree();
                functionTree.addItem(function);
            }
        } else {

            final List<ExpressionTree> arguments = new ArrayList<ExpressionTree>(arity);
            for (int i = 0; i < function.getArity(); i++) {
                final ExpressionTree subExpression = new ExpressionTree();
                final ExpressionItem lastItem = readFunctionArgument(reader, subExpression, context);

                if (SpecialItem.BRACKET_CLOSING == lastItem) {
                    arguments.add(subExpression);
                    break;
                } else if (SpecialItem.COMMA == lastItem) {
                    arguments.add(subExpression);
                    continue;
                } else {
                    throw new IllegalArgumentException("Wrong argument definition for function detected [" + function.getName() + ']');
                }
            }

            functionTree = new ExpressionTree();
            functionTree.addItem(function);
            ExpressionTreeElement functionTreeElement = functionTree.getRoot();

            if (arguments.size() != functionTreeElement.getArity()) {
                throw new IllegalArgumentException("Wrong argument number for function \'" + function.getName() + "\', it needs " + function.getArity() + " argument(s)");
            }

            functionTreeElement.fillArguments(arguments);
        }
        return functionTree;
    }

    /**
     * The auxiliary method allows to read a function argument
     * @param reader a reader to be the character source, must not be null
     * @param tree the result tree to be filled by read items, must not be null
     * @param context a preprocessor context, it can be null
     * @return the last read expression item (a comma or a bracket)
     * @throws IOException  it will be thrown if there is any error during char reading from the reader
     */
    ExpressionItem readFunctionArgument(final PushbackReader reader, final ExpressionTree tree, final PreprocessorContext context) throws IOException {
        boolean working = true;
        ExpressionItem result = null;
        while (working) {
            final ExpressionItem nextItem = nextItem(reader, context);
            if (nextItem == null) {
                throw new IllegalStateException("Non-closed function detected");
            } else if (SpecialItem.COMMA == nextItem) {
                result = nextItem;
                working = false;
            } else if (SpecialItem.BRACKET_OPENING == nextItem) {
                final ExpressionTree subExpression = new ExpressionTree();
                if (SpecialItem.BRACKET_CLOSING != readExpression(reader, subExpression, context, true, false)) {
                    throw new IllegalStateException("Non-closed bracket inside a function argument detected");
                }
                tree.addTree(subExpression);
            } else if (SpecialItem.BRACKET_CLOSING == nextItem) {
                result = nextItem;
                working = false;
            } else {
                if (nextItem.getExpressionItemType() == ExpressionItemType.FUNCTION) {
                    final AbstractFunction function = (AbstractFunction) nextItem;
                    ExpressionTree functionTree = readFunction(function, reader, context);
                    tree.addTree(functionTree);
                } else {
                    tree.addItem(nextItem);
                }
            }
        }
        return result;
    }

    private static boolean isDelimiterOrOperatorChar(final char chr) {
        return isDelimiter(chr) || isOperatorChar(chr);
    }

    private static boolean isDelimiter(final char chr) {
        switch (chr) {
            case ',':
            case '(':
            case ')':
                return true;
            default:
                return false;
        }
    }

    private static boolean isOperatorChar(final char chr) {
        switch (chr) {
            case '-':
            case '+':
            case '%':
            case '*':
            case '/':
            case '&':
            case '|':
            case '!':
            case '^':
            case '=':
            case '<':
            case '>':
                return true;
            default:
                return false;
        }
    }

    /**
     * Read the next item from the reader
     * @param reader a reader to be used as the char source, must not be null
     * @param context a preprocessor context, it can be null
     * @return a read expression item, it can be null if the end is reached
     * @throws IOException it will be thrown if there is any error during a char reading
     */
    ExpressionItem nextItem(final PushbackReader reader, final PreprocessorContext context) throws IOException {
        if (reader == null) {
            throw new NullPointerException("Reader is null");
        }
        
        ParserState state = ParserState.WAIT;
        final StringBuilder builder = new StringBuilder(12);

        boolean found = false;

        while (!found) {
            final int data = reader.read();

            if (data < 0) {
                if (state != ParserState.WAIT) {
                    found = true;
                }
                break;
            }

            final char chr = (char) data;

            switch (state) {
                case WAIT: {
                    if (Character.isWhitespace(chr)) {
                        continue;
                    } else if (chr == ',') {
                        return SpecialItem.COMMA;
                    } else if (chr == '(') {
                        return SpecialItem.BRACKET_OPENING;
                    } else if (chr == ')') {
                        return SpecialItem.BRACKET_CLOSING;
                    } else if (Character.isDigit(chr)) {
                        builder.append(chr);
                        if (chr == '0') {
                            state = ParserState.HEX_NUMBER;
                        } else {
                            state = ParserState.NUMBER;
                        }
                    } else if (chr == '.') {
                        builder.append('.');
                        state = ParserState.FLOAT_NUMBER;
                    } else if (Character.isLetter(chr) || chr == '$' || chr == '_') {
                        builder.append(chr);
                        state = ParserState.VALUE_OR_FUNCTION;
                    } else if (chr == '\"') {
                        state = ParserState.STRING;
                    } else if (isOperatorChar(chr)) {
                        builder.append(chr);
                        state = ParserState.OPERATOR;
                    } else {
                        throw new IllegalArgumentException("Unsupported token character detected \'" + chr + '\'');
                    }
                }
                break;
                case OPERATOR: {
                    if (!isOperatorChar(chr) || isDelimiter(chr)) {
                        reader.unread(data);
                        found = true;
                    } else {
                        builder.append(chr);
                    }
                }
                break;
                case FLOAT_NUMBER: {
                    if (Character.isDigit(chr)) {
                        builder.append(chr);
                    } else {
                        found = true;
                        reader.unread(data);
                    }
                }
                break;
                case HEX_NUMBER: {
                    if (builder.length() == 1) {
                        if (chr == 'X' || chr == 'x') {
                            builder.append(chr);
                        } else {
                            if (chr == '.') {
                                builder.append(chr);
                                state = ParserState.FLOAT_NUMBER;
                            } else {
                                if (Character.isDigit(chr)) {
                                    state = ParserState.NUMBER;
                                } else {
                                    state = ParserState.NUMBER;
                                    found = true;
                                    reader.unread(data);
                                }
                            }
                        }
                    } else {
                        if (Character.isDigit(chr) || (chr >= 'a' && chr <= 'f') || (chr >= 'A' && chr <= 'F')) {
                            builder.append(chr);
                        } else {
                            found = true;
                            reader.unread(data);
                        }
                    }
                }
                break;
                case NUMBER: {
                    if (Character.isDigit(chr)) {
                        builder.append(chr);
                    } else {
                        if (chr == '.') {
                            builder.append(chr);
                            state = ParserState.FLOAT_NUMBER;
                        } else {
                            reader.unread(data);
                            found = true;
                        }
                    }
                }
                break;
                case VALUE_OR_FUNCTION: {
                    if (Character.isWhitespace(chr) || isDelimiterOrOperatorChar(chr)) {
                        reader.unread(data);
                        found = true;
                    } else {
                        builder.append(chr);
                    }
                }
                break;
                case SPECIAL_CHAR: {
                    switch (chr) {
                        case 'n':
                            builder.append('\n');
                            break;
                        case 't':
                            builder.append('\t');
                            break;
                        case 'b':
                            builder.append('\b');
                            break;
                        case 'f':
                            builder.append('\f');
                            break;
                        case 'r':
                            builder.append('\r');
                            break;
                        case '\\':
                            builder.append('\\');
                            break;
                        case '\"':
                            builder.append('\"');
                            break;
                        case '\'':
                            builder.append('\'');
                            break;
                        default:
                            throw new IllegalArgumentException("Unsupported special char detected \'\\" + chr + '\'');
                    }
                    state = ParserState.STRING;
                }
                break;
                case STRING: {
                    switch (chr) {
                        case '\"': {
                            found = true;
                        }
                        break;
                        case '\\': {
                            state = ParserState.SPECIAL_CHAR;
                        }
                        break;
                        default: {
                            builder.append(chr);
                        }
                    }
                }
                break;
                default:
                    throw new Error("Unsupported parser state [" + state.name() + ']');
            }
        }

        if (!found) {
            switch (state) {
                case SPECIAL_CHAR:
                case STRING:
                    throw new IllegalStateException("Unclosed string has been detected");
                default:
                    return null;
            }
        } else {
            ExpressionItem result = null;
            switch (state) {
                case FLOAT_NUMBER: {
                    result = Value.valueOf(Float.parseFloat(builder.toString()));
                }
                break;
                case HEX_NUMBER: {
                    final String text = builder.toString();
                    if ("0".equals(text)) {
                        result = Value.INT_ZERO;
                    } else {
                        final String str = PreprocessorUtils.extractTail("0x", text);
                        result = Value.valueOf(Long.parseLong(str, 16));
                    }
                }
                break;
                case NUMBER: {
                    result = Value.valueOf(Long.parseLong(builder.toString()));
                }
                break;
                case OPERATOR: {
                    final String operatorLC = builder.toString().toLowerCase();
                    for (final AbstractOperator operator : AbstractOperator.ALL_OPERATORS) {
                        if (operator.getKeyword().equals(operatorLC)) {
                            result = operator;
                            break;
                        }
                    }

                    if (result == null) {
                        throw new IllegalArgumentException("Unknown operator detected \'" + operatorLC + '\'');
                    }
                }
                break;
                case STRING: {
                    result = Value.valueOf(builder.toString());
                }
                break;
                case VALUE_OR_FUNCTION: {
                    final String str = builder.toString().toLowerCase();
                    if (str.charAt(0) == '$') {
                        if (context == null) {
                            throw new IllegalStateException("There is not a preprocessor context to define a user function [" + str + ']');
                        }

                        final PreprocessorExtension extension = context.getPreprocessorExtension();
                        if (extension == null) {
                            throw new IllegalStateException("There is not any defined preprocessor extension to get data about user functions [" + str + ']');
                        }

                        final String userFunctionName = PreprocessorUtils.extractTail("$", str);

                        // user defined
                        result = new FunctionDefinedByUser(userFunctionName, extension.getUserFunctionArity(userFunctionName), context);
                    } else {
                        if ("true".equals(str)) {
                            result = Value.BOOLEAN_TRUE;
                        } else if ("false".equals(str)) {
                            result = Value.BOOLEAN_FALSE;
                        } else {
                            final AbstractFunction function = AbstractFunction.findForName(str);
                            if (function == null) {
                                result = new Variable(str);
                            } else {
                                result = function;
                            }
                        }
                    }
                }
                break;
                default: {
                    throw new Error("Unsupported final parser state detected [" + state.name() + ']');
                }
            }
            return result;
        }
    }
}