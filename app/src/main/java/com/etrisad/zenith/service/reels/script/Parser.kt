/*
 * Ported from Curbox (https://github.com/iecal/curbox-android-kt-rewrite).
 * Original work: Copyright (c) Curbox contributors.
 * Adapted for Zenith: package renamed, feature-specific integration.
 */
package com.etrisad.zenith.service.reels.script

/**
 * Recursive-descent parser turning a token list into a list of [Stmt].
 * Throws [ScriptError] (with a line number) on the first syntax error.
 *
 * Precedence, low to high: or · and · equality · comparison · range(`..`) ·
 * `+ -` · `* / %` · unary(`not -`) · postfix(call/index/member) · primary.
 */
class Parser(private val tokens: List<Token>) {

    private var current = 0

    companion object {
        /** Convenience: source -> AST. The single entry point used by the runtime and editor. */
        fun parse(source: String): List<Stmt> = Parser(Lexer(source).scan()).parseProgram()
    }

    fun parseProgram(): List<Stmt> {
        val statements = ArrayList<Stmt>()
        while (!isAtEnd()) statements.add(statement())
        return statements
    }

    private fun statement(): Stmt {
        return when {
            check(TokenType.IF) -> ifStatement()
            check(TokenType.WHILE) -> whileStatement()
            check(TokenType.FOR) -> forStatement()
            check(TokenType.FN) -> fnDeclaration()
            check(TokenType.RETURN) -> returnStatement()
            check(TokenType.BREAK) -> { advance(); Stmt.Break(previous().line) }
            check(TokenType.CONTINUE) -> { advance(); Stmt.Continue(previous().line) }
            check(TokenType.LBRACE) -> block()
            check(TokenType.IDENT) && checkNext(TokenType.ASSIGN) -> assignment()
            else -> Stmt.ExprStmt(expression(), peek().line)
        }
    }

    private fun assignment(): Stmt {
        val name = advance()           // IDENT
        advance()                      // ASSIGN
        val value = expression()
        return Stmt.Assign(name.lexeme, value, name.line)
    }

    private fun block(): Stmt.Block {
        val line = consume(TokenType.LBRACE, "expected '{'").line
        val statements = ArrayList<Stmt>()
        while (!check(TokenType.RBRACE) && !isAtEnd()) statements.add(statement())
        consume(TokenType.RBRACE, "expected '}'")
        return Stmt.Block(statements, line)
    }

    private fun ifStatement(): Stmt {
        val line = consume(TokenType.IF, "expected 'if'").line
        val branches = ArrayList<Pair<Expr, Stmt.Block>>()
        branches.add(expression() to block())
        var elseBranch: Stmt.Block? = null
        while (match(TokenType.ELSE)) {
            if (match(TokenType.IF)) {
                branches.add(expression() to block())
            } else {
                elseBranch = block()
                break
            }
        }
        return Stmt.If(branches, elseBranch, line)
    }

    private fun whileStatement(): Stmt {
        val line = consume(TokenType.WHILE, "expected 'while'").line
        val condition = expression()
        return Stmt.While(condition, block(), line)
    }

    private fun forStatement(): Stmt {
        val line = consume(TokenType.FOR, "expected 'for'").line
        val name = consume(TokenType.IDENT, "expected loop variable name")
        consume(TokenType.IN, "expected 'in'")
        val iterable = expression()
        return Stmt.For(name.lexeme, iterable, block(), line)
    }

    private fun fnDeclaration(): Stmt {
        val line = consume(TokenType.FN, "expected 'fn'").line
        val name = consume(TokenType.IDENT, "expected function name")
        consume(TokenType.LPAREN, "expected '(' after function name")
        val params = ArrayList<String>()
        if (!check(TokenType.RPAREN)) {
            do {
                params.add(consume(TokenType.IDENT, "expected parameter name").lexeme)
            } while (match(TokenType.COMMA))
        }
        consume(TokenType.RPAREN, "expected ')'")
        return Stmt.FnDecl(name.lexeme, params, block(), line)
    }

    private fun returnStatement(): Stmt {
        val line = consume(TokenType.RETURN, "expected 'return'").line
        val value = if (check(TokenType.RBRACE) || isAtEnd() || peek().line != line) null else expression()
        return Stmt.Return(value, line)
    }

    private fun expression(): Expr = or()

    private fun or(): Expr {
        var expr = and()
        while (match(TokenType.OR)) {
            val op = previous()
            expr = Expr.Logical(expr, op.type, and(), op.line)
        }
        return expr
    }

    private fun and(): Expr {
        var expr = equality()
        while (match(TokenType.AND)) {
            val op = previous()
            expr = Expr.Logical(expr, op.type, equality(), op.line)
        }
        return expr
    }

    private fun equality(): Expr {
        var expr = comparison()
        while (match(TokenType.EQ, TokenType.NEQ)) {
            val op = previous()
            expr = Expr.Binary(expr, op.type, comparison(), op.line)
        }
        return expr
    }

    private fun comparison(): Expr {
        var expr = range()
        while (match(TokenType.LT, TokenType.LTE, TokenType.GT, TokenType.GTE)) {
            val op = previous()
            expr = Expr.Binary(expr, op.type, range(), op.line)
        }
        return expr
    }

    private fun range(): Expr {
        val expr = term()
        if (match(TokenType.DOTDOT)) {
            val op = previous()
            return Expr.Range(expr, term(), op.line)
        }
        return expr
    }

    private fun term(): Expr {
        var expr = factor()
        while (match(TokenType.PLUS, TokenType.MINUS)) {
            val op = previous()
            expr = Expr.Binary(expr, op.type, factor(), op.line)
        }
        return expr
    }

    private fun factor(): Expr {
        var expr = unary()
        while (match(TokenType.STAR, TokenType.SLASH, TokenType.PERCENT)) {
            val op = previous()
            expr = Expr.Binary(expr, op.type, unary(), op.line)
        }
        return expr
    }

    private fun unary(): Expr {
        if (match(TokenType.NOT, TokenType.MINUS)) {
            val op = previous()
            return Expr.Unary(op.type, unary(), op.line)
        }
        return postfix()
    }

    private fun postfix(): Expr {
        var expr = primary()
        while (true) {
            expr = when {
                match(TokenType.LPAREN) -> finishCall(expr)
                match(TokenType.DOT) -> {
                    val name = consume(TokenType.IDENT, "expected property name after '.'")
                    Expr.Member(expr, name.lexeme, name.line)
                }
                match(TokenType.LBRACKET) -> {
                    val line = previous().line
                    val index = expression()
                    consume(TokenType.RBRACKET, "expected ']'")
                    Expr.Index(expr, index, line)
                }
                else -> return expr
            }
        }
    }

    private fun finishCall(callee: Expr): Expr {
        val line = previous().line
        val args = ArrayList<Expr>()
        val named = LinkedHashMap<String, Expr>()
        if (!check(TokenType.RPAREN)) {
            do {
                if (check(TokenType.IDENT) && checkNext(TokenType.ASSIGN)) {
                    val name = advance().lexeme
                    advance() // ASSIGN
                    named[name] = expression()
                } else {
                    args.add(expression())
                }
            } while (match(TokenType.COMMA))
        }
        consume(TokenType.RPAREN, "expected ')' after arguments")
        return Expr.Call(callee, args, named, line)
    }

    private fun primary(): Expr {
        val token = peek()
        return when (token.type) {
            TokenType.NUMBER -> { advance(); Expr.Literal(token.literal, token.line) }
            TokenType.STRING -> { advance(); Expr.Literal(token.literal, token.line) }
            TokenType.TRUE -> { advance(); Expr.Literal(true, token.line) }
            TokenType.FALSE -> { advance(); Expr.Literal(false, token.line) }
            TokenType.NULL -> { advance(); Expr.Literal(null, token.line) }
            TokenType.IDENT -> { advance(); Expr.Variable(token.lexeme, token.line) }
            TokenType.LPAREN -> {
                advance()
                val expr = expression()
                consume(TokenType.RPAREN, "expected ')'")
                expr
            }
            TokenType.LBRACKET -> {
                advance()
                val elements = ArrayList<Expr>()
                if (!check(TokenType.RBRACKET)) {
                    do { elements.add(expression()) } while (match(TokenType.COMMA))
                }
                consume(TokenType.RBRACKET, "expected ']'")
                Expr.ListLiteral(elements, token.line)
            }
            else -> throw ScriptError("unexpected '${token.lexeme.ifEmpty { "end of input" }}'", token.line)
        }
    }

    private fun match(vararg types: TokenType): Boolean {
        if (types.any { check(it) }) { advance(); return true }
        return false
    }

    private fun check(type: TokenType): Boolean = !isAtEnd() && peek().type == type
    private fun checkNext(type: TokenType): Boolean {
        if (current + 1 >= tokens.size) return false
        return tokens[current + 1].type == type
    }

    private fun consume(type: TokenType, message: String): Token {
        if (check(type)) return advance()
        throw ScriptError(message + " but found '${peek().lexeme.ifEmpty { "end of input" }}'", peek().line)
    }

    private fun advance(): Token { if (!isAtEnd()) current++; return previous() }
    private fun previous(): Token = tokens[current - 1]
    private fun peek(): Token = tokens[current]
    private fun isAtEnd(): Boolean = peek().type == TokenType.EOF
}
