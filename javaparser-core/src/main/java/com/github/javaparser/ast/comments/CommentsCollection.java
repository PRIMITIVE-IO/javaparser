/*
 * Copyright (C) 2007-2010 Júlio Vilmar Gesser.
 * Copyright (C) 2011, 2013-2020 The JavaParser Team.
 *
 * This file is part of JavaParser.
 *
 * JavaParser can be used either under the terms of
 * a) the GNU Lesser General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * b) the terms of the Apache License
 *
 * You should have received a copy of both licenses in LICENCE.LGPL and
 * LICENCE.APACHE. Please refer to those files for details.
 *
 * JavaParser is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 */

package com.github.javaparser.ast.comments;

import com.github.javaparser.Range;

import java.util.*;

import static com.github.javaparser.ast.Node.NODE_BY_BEGIN_POSITION;

/**
 * The comments contained in a certain parsed piece of source code.
 */
public class CommentsCollection {
    private final TreeSet<Comment> comments = new TreeSet<>(NODE_BY_BEGIN_POSITION);

    public CommentsCollection() {
    }

    public CommentsCollection(Collection<Comment> commentsToCopy) {
        comments.addAll(commentsToCopy);
    }

    public Set<LineComment> getLineComments() {
        TreeSet<LineComment> lineComments = new TreeSet<>(NODE_BY_BEGIN_POSITION);
        for (Comment comment : comments) {
            if (comment instanceof LineComment) {
                LineComment lineComment = (LineComment) comment;
                lineComments.add(lineComment);
            }
        }
        return lineComments;
    }

    public Set<BlockComment> getBlockComments() {
        TreeSet<BlockComment> blockComments = new TreeSet<>(NODE_BY_BEGIN_POSITION);
        for (Comment comment : comments) {
            if (comment instanceof BlockComment) {
                BlockComment blockComment = (BlockComment) comment;
                blockComments.add(blockComment);
            }
        }
        return blockComments;
    }

    public Set<JavadocComment> getJavadocComments() {
        TreeSet<JavadocComment> javadocComments = new TreeSet<>(NODE_BY_BEGIN_POSITION);
        for (Comment comment : comments) {
            if (comment instanceof JavadocComment) {
                JavadocComment javadocComment = (JavadocComment) comment;
                javadocComments.add(javadocComment);
            }
        }
        return javadocComments;
    }

    public void addComment(Comment comment) {
        comments.add(comment);
    }

    public boolean contains(Comment comment) {
        if (!comment.getRange().isPresent()) {
            return false;
        }
        Range commentRange = comment.getRange().get();
        for (Comment c : getComments()) {
            if (!c.getRange().isPresent()) {
                return false;
            }
            Range cRange = c.getRange().get();
            // we tolerate a difference of one element in the end column:
            // it depends how \r and \n are calculated...
            if (cRange.begin.equals(commentRange.begin) &&
                    cRange.end.line == commentRange.end.line &&
                    Math.abs(cRange.end.column - commentRange.end.column) < 2) {
                return true;
            }
        }
        return false;
    }

    public TreeSet<Comment> getComments() {
        return comments;
    }

    public int size() {
        return comments.size();
    }

    public CommentsCollection minus(CommentsCollection other) {
        CommentsCollection result = new CommentsCollection();
        List<Comment> list = new ArrayList<>();
        for (Comment comment : comments) {
            if (!other.contains(comment)) {
                list.add(comment);
            }
        }
        result.comments.addAll(
                list);
        return result;
    }

    public CommentsCollection copy() {
        return new CommentsCollection(comments);
    }
}
