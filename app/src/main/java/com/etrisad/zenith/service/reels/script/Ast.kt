/*
 * Ported from Curbox (https://github.com/iecal/curbox-android-kt-rewrite).
 * Original work: Copyright (c) Curbox contributors.
 * Adapted for Zenith: package renamed, feature-specific integration.
 */
package com.etrisad.zenith.service.reels.script

/** Expression nodes. Every node carries the source [line] for error reporting. */
sealed class Expr {
    abstract val line: Int

    class Literal(val value: Any?, override val line: Int) : Expr()
    class ListLiteral(val elements: List<Expr>, override val line: Int) : Expr()
    class Variable(val name: String, override val line: Int) : Expr()
    class Unary(val op: TokenType, val operand: Expr, override val line: Int) : Expr()
    class Binary(val left: Expr, val op: TokenType, val right: Expr, override val line: Int) : Expr()
    class Logical(val left: Expr, val op: TokenType, val right: Expr, override val line: Int) : Expr()
    class Range(val start: Expr, val end: Expr, override val line: Int) : Expr()
    class Index(val target: Expr, val index: Expr, override val line: Int) : Expr()
    class Member(val target: Expr, val name: String, override val line: Int) : Expr()

    /** A call. [callee] is a bare name for global calls, or a [Member] for method calls. */
    class Call(
        val callee: Expr,
        val args: List<Expr>,
        val named: Map<String, Expr>,
        override val line: Int
    ) : Expr()
}

/** Statement nodes. */
sealed class Stmt {
    abstract val line: Int

    class ExprStmt(val expr: Expr, override val line: Int) : Stmt()
    class Assign(val name: String, val value: Expr, override val line: Int) : Stmt()
    class Block(val statements: List<Stmt>, override val line: Int) : Stmt()
    class If(
        val branches: List<Pair<Expr, Block>>,
        val elseBranch: Block?,
        override val line: Int
    ) : Stmt()
    class While(val condition: Expr, val body: Block, override val line: Int) : Stmt()
    class For(val varName: String, val iterable: Expr, val body: Block, override val line: Int) : Stmt()
    class FnDecl(val name: String, val params: List<String>, val body: Block, override val line: Int) : Stmt()
    class Return(val value: Expr?, override val line: Int) : Stmt()
    class Break(override val line: Int) : Stmt()
    class Continue(override val line: Int) : Stmt()
}
