package com.pr0gramm.app.ui.views

import android.annotation.SuppressLint
import android.support.v4.content.ContextCompat
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.google.common.base.Ascii.equalsIgnoreCase
import com.pr0gramm.app.R
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.orm.Vote
import com.pr0gramm.app.services.ThemeHelper
import com.pr0gramm.app.util.AndroidUtility
import com.pr0gramm.app.util.observeChange
import com.pr0gramm.app.util.visible
import gnu.trove.TCollections
import gnu.trove.map.TLongObjectMap
import gnu.trove.map.hash.TLongObjectHashMap
import gnu.trove.set.TLongSet
import gnu.trove.set.hash.TLongHashSet
import kotterknife.bindOptionalView
import kotterknife.bindView
import org.joda.time.Hours
import org.joda.time.Instant.now
import java.util.Collections.emptyList

/**
 */
class CommentsAdapter(private val admin: Boolean, private val selfName: String) : RecyclerView.Adapter<CommentsAdapter.CommentView>() {
    private var comments: List<CommentEntry> = emptyList()

    private var op: String? = null

    private val scoreVisibleThreshold = now().minus(Hours.ONE.toStandardDuration())

    var showFavCommentButton: Boolean = false

    var favedComments: TLongSet by observeChange(TLongHashSet()) { notifyDataSetChanged() }

    var commentActionListener: CommentActionListener? = null

    var selectedCommentId by observeChange(0L) { notifyDataSetChanged() }

    init {
        setHasStableIds(true)
        set(emptyList(), NO_VOTES, null)
    }

    fun set(comments: Collection<Api.Comment>, votes: TLongObjectMap<Vote>, op: String?) {
        this.op = op

        val byId = comments.associateBy { it.id }

        this.comments = sort(comments, op).map { comment ->
            val depth = getCommentDepth(byId, comment)
            val baseVote = votes.get(comment.id) ?: Vote.NEUTRAL
            CommentEntry(comment, baseVote, depth)
        }

        notifyDataSetChanged()
    }

    override fun getItemCount(): Int {
        return comments.size
    }

    override fun getItemId(position: Int): Long {
        return comments[position].comment.id
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentView {
        return CommentView(LayoutInflater
                .from(parent.context)
                .inflate(R.layout.comment_layout, parent, false))
    }

    override fun onViewRecycled(holder: CommentView?) {
        super.onViewRecycled(holder)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: CommentView, position: Int) {
        val entry = comments[position]
        val comment = entry.comment
        val context = holder.itemView.context

        holder.updateCommentDepth(entry.depth)
        holder.senderInfo.setSenderName(comment.name, comment.mark)
        holder.senderInfo.setOnSenderClickedListener {
            commentActionListener?.onCommentAuthorClicked(comment)
        }

        AndroidUtility.linkify(holder.comment, comment.content)

        // show the points
        if (admin
                || equalsIgnoreCase(comment.name, selfName)
                || comment.created.isBefore(scoreVisibleThreshold)) {

            holder.senderInfo.setPoints(getCommentScore(entry))
        } else {
            holder.senderInfo.setPointsUnknown()
        }

        // and the date of the post
        holder.senderInfo.setDate(comment.created)

        // enable or disable the badge
        holder.senderInfo.setBadgeOpVisible(op == comment.name)

        // and register a vote handler
        holder.vote.setVote(entry.vote, true)
        holder.vote.onVote = { vote ->
            val changed = doVote(entry, vote)
            notifyItemChanged(position)
            changed
        }

        // set alpha for the sub views. sadly, setting alpha on view.itemView is not working
        holder.comment.alpha = if (entry.vote === Vote.DOWN) 0.5f else 1f
        holder.senderInfo.alpha = if (entry.vote === Vote.DOWN) 0.5f else 1f

        holder.reply.setOnClickListener {
            commentActionListener?.onAnswerClicked(comment)
        }

        holder.copyCommentLink.setOnClickListener {
            commentActionListener?.onCopyCommentLink(comment)
        }

        holder.copyCommentLink.setOnLongClickListener {
            commentActionListener?.onDeleteCommentClicked(comment) ?: false
        }

        if (comment.id == selectedCommentId) {
            val color = ContextCompat.getColor(context, R.color.selected_comment_background)
            holder.itemView.setBackgroundColor(color)
        } else {
            AndroidUtility.setViewBackground(holder.itemView, null)
        }

        holder.kFav?.let { kFav ->
            if (showFavCommentButton) {
                val isFavorite = favedComments.contains(comment.id)

                if (isFavorite) {
                    val color = ContextCompat.getColor(context, ThemeHelper.accentColor)
                    kFav.setColorFilter(color)
                    kFav.setImageResource(R.drawable.ic_favorite)
                } else {
                    val color = ContextCompat.getColor(context, R.color.grey_700)
                    kFav.setColorFilter(color)
                    kFav.setImageResource(R.drawable.ic_favorite_border)
                }

                kFav.visibility = View.VISIBLE
                kFav.setOnClickListener {
                    commentActionListener?.onCommentMarkAsFavoriteClicked(comment, !isFavorite)
                }
            } else {
                kFav.visibility = View.GONE
            }
        }
    }

    private fun getCommentScore(entry: CommentEntry): CommentScore {
        var score = 0
        score += entry.comment.up - entry.comment.down
        score += entry.vote.voteValue - entry.baseVote.voteValue
        return CommentScore(score, entry.comment.up, entry.comment.down)
    }

    private fun doVote(entry: CommentEntry, vote: Vote): Boolean {
        val performVote = commentActionListener?.onCommentVoteClicked(entry.comment, vote) ?: false
        if (performVote) {
            entry.vote = vote
        }

        return performVote
    }

    /**
     * "Flattens" a list of hierarchical comments to a sorted list of comments.

     * @param comments The comments to sort
     */
    private fun sort(comments: Collection<Api.Comment>, op: String?): List<Api.Comment> {
        // index all comments by their parent in a multimap.
        val byParent = TLongObjectHashMap<MutableList<Api.Comment>>()
        for (comment in comments) {
            val children = byParent[comment.parent] ?: run {
                val newList = mutableListOf<Api.Comment>()
                byParent.put(comment.parent, newList)
                newList
            }

            children.add(comment)
        }

        val result = mutableListOf<Api.Comment>()
        appendChildComments(result, byParent, 0, op)
        return result
    }

    private fun appendChildComments(target: MutableList<Api.Comment>,
                                    byParent: TLongObjectHashMap<MutableList<Api.Comment>>,
                                    id: Long, op: String?) {

        val ordering: Comparator<Api.Comment> =
                compareByDescending<Api.Comment> { it.name == op }.thenByDescending { it.confidence }

        byParent[id]?.sortedWith(ordering)?.forEach { child ->
            target.add(child)
            appendChildComments(target, byParent, child.id, op)
        }
    }

    private fun getCommentDepth(byId: Map<Long, Api.Comment>, comment: Api.Comment): Int {
        var current = comment
        var depth = 0

        while (true) {
            depth++
            current = byId[current.parent] ?: break
        }

        return depth
    }

    class CommentView(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val vote: VoteView by bindView(R.id.voting)
        val reply: ImageView by bindView(R.id.reply)
        val comment: TextView by bindView(R.id.comment)
        val senderInfo: SenderInfoView by bindView(R.id.sender_info)
        val copyCommentLink: View by bindView(R.id.copy_comment_link)

        val kFav: ImageView? by bindOptionalView(R.id.kfav)
        val commentSpacerView = itemView as CommentSpacerView

        fun updateCommentDepth(depth: Int) {
            commentSpacerView.depth = depth
            reply.visible = depth < MAX_COMMENT_DEPTH
        }
    }

    private class CommentEntry(val comment: Api.Comment, val baseVote: Vote, val depth: Int) {
        var vote: Vote = baseVote
    }

    interface CommentActionListener {
        fun onCommentVoteClicked(comment: Api.Comment, vote: Vote): Boolean

        fun onAnswerClicked(comment: Api.Comment)

        fun onCommentAuthorClicked(comment: Api.Comment)

        fun onCommentMarkAsFavoriteClicked(comment: Api.Comment, markAsFavorite: Boolean)

        fun onCopyCommentLink(comment: Api.Comment)

        fun onDeleteCommentClicked(comment: Api.Comment): Boolean
    }

    companion object {
        private val NO_VOTES = TCollections.unmodifiableMap(TLongObjectHashMap<Vote>())
        private val MAX_COMMENT_DEPTH = 15
    }
}
