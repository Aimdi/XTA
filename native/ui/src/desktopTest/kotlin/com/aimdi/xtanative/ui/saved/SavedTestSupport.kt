package com.aimdi.xtanative.ui.saved

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.store.StoreDir
import com.aimdi.xtanative.store.saved.LikedPost
import com.aimdi.xtanative.store.saved.SavedPost
import java.nio.file.Files
import kotlinx.serialization.json.JsonElement

/** [text] as a JSON element: the tests write their fixtures inline. */
fun json(text: String): JsonElement = J.parse(text).raw ?: error("Not JSON: $text")

/** A store folder of its own, in a fresh temporary folder. */
fun tempStore(): StoreDir = StoreDir(Files.createTempDirectory("ui-saved-test").toFile())

/** An X post as XTA keeps it, by [id], with a photo when [photo]. */
fun xTweet(id: String, text: String = "post $id", photo: Boolean = false, author: String = "u$id"): JsonElement = json(
    """{"id_str":"$id","full_text":"$text","user":{"id_str":"$author","screen_name":"user$id","name":"User $id"}${
        if (photo) ""","extended_entities":{"media":[{"id_str":"m$id","type":"photo","media_url_https":"https://pbs.twimg.com/media/m$id.jpg"}]}""" else ""
    }}""",
)

fun savedX(id: String, photo: Boolean = false, folder: String? = null, author: String = "u$id") =
    SavedPost(id, author, xTweet(id, photo = photo, author = author), folderId = folder)

fun likedX(id: String, author: String = "u$id", photo: Boolean = false) = LikedPost(id, author, xTweet(id, photo = photo, author = author))

/** A Reddit post as XTA's archive blob keeps it; with a picture when [picture]. */
fun savedReddit(id: String, picture: Boolean = false) = SavedPost(
    "reddit:$id",
    "pics",
    json(
        """{"xtaPlugin":"reddit","post":{"id":"$id","title":"Cat $id","subreddit":"pics","author":"cats","permalink":"/r/pics/$id","url":"${
            if (picture) "https://i.redd.it/$id.jpg" else "https://example.com/$id"
        }","selfText":"Look"}}""",
    ),
)
