/*
 * Ported from Curbox (https://github.com/iecal/curbox-android-kt-rewrite).
 * Original work: Copyright (c) Curbox contributors.
 * Adapted for Zenith: package renamed, feature-specific integration.
 */
package com.etrisad.zenith.service.reels.script

enum class TokenType {
    // literals
    NUMBER, STRING, IDENT,
    // keywords
    IF, ELSE, WHILE, FOR, IN, FN, RETURN, BREAK, CONTINUE,
    TRUE, FALSE, NULL, AND, OR, NOT,
    // punctuation / operators
    LPAREN, RPAREN, LBRACE, RBRACE, LBRACKET, RBRACKET,
    COMMA, DOT, DOTDOT,
    ASSIGN,                 // = or :=
    PLUS, MINUS, STAR, SLASH, PERCENT,
    EQ, NEQ, LT, LTE, GT, GTE,
    EOF
}

data class Token(
    val type: TokenType,
    val lexeme: String,
    val literal: Any? = null,
    val line: Int
)

/**
 * Converts script source into a flat token list. Comments start with `#` and run to the
 * end of the line. Strings are double-quoted with `\n`, `\t`, `\"`, `\\` escapes.
 */
class Lexer(private val source: String) {

    private val tokens = ArrayList<Token>()
    private var start = 0
    private var current = 0
    private var line = 1

    private val keywords = mapOf(
        "if" to TokenType.IF, "else" to TokenType.ELSE, "while" to TokenType.WHILE,
        "for" to TokenType.FOR, "in" to TokenType.IN, "fn" to TokenType.FN,
        "return" to TokenType.RETURN, "break" to TokenType.BREAK, "continue" to TokenType.CONTINUE,
        "true" to TokenType.TRUE, "false" to TokenType.FALSE, "null" to TokenType.NULL,
        "and" to TokenType.AND, "or" to TokenType.OR, "not" to TokenType.NOT
    )

    fun scan(): List<Token> {
        while (!isAtEnd()) {
            start = current
            scanToken()
        }
        tokens.add(Token(TokenType.EOF, "", null, line))
        return tokens
    }

    private fun scanToken() {
        val c = advance()
        when (c) {
            ' ', '\r', '\t' -> {}
            '\n' -> line++
            '#' -> while (peek() != '\n' && !isAtEnd()) advance()
            '(' -> add(TokenType.LPAREN)
            ')' -> add(TokenType.RPAREN)
            '{' -> add(TokenType.LBRACE)
            '}' -> add(TokenType.RBRACE)
            '[' -> add(TokenType.LBRACKET)
            ']' -> add(TokenType.RBRACKET)
            ',' -> add(TokenType.COMMA)
            '+' -> add(TokenType.PLUS)
            '-' -> add(TokenType.MINUS)
            '*' -> add(TokenType.STAR)
            '/' -> add(TokenType.SLASH)
            '%' -> add(TokenType.PERCENT)
            '.' -> if (match('.')) add(TokenType.DOTDOT) else add(TokenType.DOT)
            ':' -> if (match('=')) add(TokenType.ASSIGN) else throw ScriptError("unexpected ':'", line)
            '=' -> if (match('=')) add(TokenType.EQ) else add(TokenType.ASSIGN)
            '!' -> if (match('=')) add(TokenType.NEQ) else throw ScriptError("unexpected '!'", line)
            '<' -> if (match('=')) add(TokenType.LTE) else add(TokenType.LT)
            '>' -> if (match('=')) add(TokenType.GTE) else add(TokenType.GT)
            '"' -> string()
            else -> when {
                c.isDigit() -> number()
                c.isLetter() || c == '_' -> identifier()
                else -> throw ScriptError("unexpected character '$c'", line)
            }
        }
    }

    private fun string() {
        val sb = StringBuilder()
        while (peek() != '"' && !isAtEnd()) {
            val ch = advance()
            if (ch == '\n') line++
            if (ch == '\\') {
                when (val esc = advance()) {
                    'n' -> sb.append('\n')
                    't' -> sb.append('\t')
                    '"' -> sb.append('"')
                    '\\' -> sb.append('\\')
                    else -> sb.append(esc)
                }
            } else {
                sb.append(ch)
            }
        }
        if (isAtEnd()) throw ScriptError("unterminated string", line)
        advance() // closing "
        tokens.add(Token(TokenType.STRING, sb.toString(), sb.toString(), line))
    }

    private fun number() {
        while (peek().isDigit()) advance()
        // A '.' is only part of the number if followed by a digit (so `node.x` and `a..b` work).
        if (peek() == '.' && peekNext().isDigit()) {
            advance()
            while (peek().isDigit()) advance()
        }
        val text = source.substring(start, current)
        tokens.add(Token(TokenType.NUMBER, text, text.toDouble(), line))
    }

    private fun identifier() {
        while (peek().isLetterOrDigit() || peek() == '_') advance()
        val text = source.substring(start, current)
        add(keywords[text] ?: TokenType.IDENT)
    }

    private fun add(type: TokenType) {
        tokens.add(Token(type, source.substring(start, current), null, line))
    }

    private fun advance(): Char = source[current++]

    private fun match(expected: Char): Boolean {
        if (isAtEnd() || source[current] != expected) return false
        current++
        return true
    }

    private fun peek(): Char = if (isAtEnd()) '\u0000' else source[current]
    private fun peekNext(): Char = if (current + 1 >= source.length) '\u0000' else source[current + 1]
    private fun isAtEnd(): Boolean = current >= source.length
}
