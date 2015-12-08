package com.pr0gramm.app.ui.fragments;

import com.pr0gramm.app.ActivityComponent;
import com.pr0gramm.app.Settings;
import com.pr0gramm.app.api.pr0gramm.response.Message;
import com.pr0gramm.app.services.CommentService;
import com.pr0gramm.app.services.UserService;
import com.pr0gramm.app.ui.MessageAdapter;
import com.pr0gramm.app.ui.MessageView;

import java.util.List;

import javax.inject.Inject;

import static com.google.common.collect.Lists.transform;

/**
 */
public class FavedCommentFragment extends MessageInboxFragment {
    @Inject
    UserService userService;

    @Inject
    CommentService commentService;

    @Inject
    Settings settings;

    @Override
    protected LoaderHelper<List<Message>> newLoaderHelper() {
        return LoaderHelper.of(() -> {
            return commentService
                    .list(settings.getContentType())
                    .map(comments -> transform(comments, CommentService::commentToMessage));
        });
    }

    @Override
    protected MessageAdapter newMessageAdapter(List<Message> messages) {
        MessageAdapter adapter = super.newMessageAdapter(messages);
        adapter.setPointsVisibility(MessageView.PointsVisibility.NEVER);
        return adapter;
    }

    @Override
    protected void injectComponent(ActivityComponent activityComponent) {
        activityComponent.inject(this);
    }
}