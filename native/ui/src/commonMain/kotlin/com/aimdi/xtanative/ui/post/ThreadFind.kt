package com.aimdi.xtanative.ui.post

import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.parse.XText
import com.aimdi.xtanative.ui.article.ArticleLayout
import com.aimdi.xtanative.ui.common.FindBlock

/**
 * What the find bar looks through on an opened post ([PostScreen]): the
 * posts it answers, the post itself (its article in full), and its replies,
 * each text in the row of the screen's list it is drawn in, as the list lays
 * its rows out.
 */
object ThreadFind {
    /** [state]'s texts in the list's order; the replies only where they are [repliesShown] (zen keeps them hidden), as a tree when [threaded]. */
    fun blocks(state: PostUi, threaded: Boolean, repliesShown: Boolean): List<FindBlock> {
        var item = state.withheldBefore.size
        val blocks = ArrayList<FindBlock>()
        state.before.forEach { blocks += of(it, item++) }
        val focal = state.focal
        when {
            focal != null -> {
                blocks += of(focal, item)
                ArticleLayout.fullArticle(focal.shown)?.let { blocks += ArticleLayout.findBlocks(it, item) }
                item++
            }
            state.focalWithheld != null || state.gone -> item++
        }
        if ((state.loading && state.replies.isEmpty()) || state.problem != null) item++
        if (!repliesShown) return blocks
        if (threaded) {
            ThreadTree.rows(ThreadTree.nodes(state.replies), state.collapsed).forEach { row ->
                if (row is ThreadRow.Reply) row.node.thread.posts.forEach { blocks += of(it, item) }
                item++
            }
        } else {
            state.replies.forEach { thread -> thread.posts.forEach { blocks += of(it, item) }; item++ }
        }
        return blocks
    }

    /** The texts [post] draws in row [item]: its own (none with an article, drawn apart) and the post it quotes. */
    fun of(post: XPost, item: Int): List<FindBlock> {
        val shown = post.shown
        val own = FindBlock(shown.id, textOf(shown), item).takeIf { shown.article == null }
        val quote = shown.quoted?.takeIf { it.article == null }?.let { FindBlock(quoteFindKey(it.id), textOf(it), item) }
        return listOfNotNull(own, quote)
    }

    private fun textOf(post: XPost): String = XText.runs(post).joinToString("") { it.text }
}
