package indi.goldenwater.chaosmusicplayer.music

import indi.goldenwater.chaosmusicplayer.ChaosMusicPlayer
import indi.goldenwater.chaosmusicplayer.command.CommandCMP
import indi.goldenwater.chaosmusicplayer.music.MusicManager.RequestType.Invite
import indi.goldenwater.chaosmusicplayer.music.MusicManager.RequestType.Join
import indi.goldenwater.chaosmusicplayer.type.MusicInfo
import org.bukkit.entity.Player
import java.io.File
import java.io.FileFilter

object MusicManager {
    var musicFolder: File = File("musics")

    private val musicPlaying: MutableMap<Player, MusicPlayer> = mutableMapOf()
    private val pendingRequests: MutableList<RequestInfo> = mutableListOf()

    fun getMusics(): MutableList<MusicInfo> {
        val musicFiles = musicFolder.listFiles(FileFilter { it.extension == "wav" })?.toMutableList()
        val musicInfos = ChaosMusicPlayer.instance.getMusicInfos()

        musicFiles?.removeIf { file ->
            musicInfos.find { it.musicFile.canonicalPath == file.canonicalPath } != null
        }

        musicFiles?.forEach {
            musicInfos.add(MusicInfo(it.relativeTo(musicFolder).path))
        }

        return musicInfos
    }

    fun getPlayingMusicInfo(player: Player): MusicInfo? = musicPlaying[player]?.musicInfo

    fun play(player: Player, musicInfo: MusicInfo) {
        val previousPlaying = musicPlaying[player]
        val playing = MusicPlayer(
            musicInfo,
            player,
        )

        if (previousPlaying != null) {
            previousPlaying.stop()
            playing.listenTogether.addAll(previousPlaying.listenTogether)
            musicPlaying.remove(player)
        }

        musicPlaying[player] = playing
        playing.runTaskAsynchronously(ChaosMusicPlayer.instance)

        player.sendMessage("开始播放 ${musicInfo.displayName}")
    }

    fun pause(player: Player) {
        val playing = musicPlaying[player]
        if (playing == null) {
            player.sendMessage("暂停播放失败, 未开始播放或你不是主持")
        } else {
            playing.pause()
            player.sendMessage("已暂停播放")
        }
    }

    fun resume(player: Player) {
        val playing = musicPlaying[player]
        if (playing == null) {
            player.sendMessage("无法继续播放, 未开始播放或你不是主持")
        } else {
            playing.resume()
            player.sendMessage("已继续播放")
        }
    }

    fun stop(player: Player) {
        val playing = musicPlaying[player]
        if (playing == null) {
            player.sendMessage("无法停止播放, 未开始播放或你不是主持")
        } else {
            playing.stop()
            musicPlaying.remove(player)
            player.sendMessage("已停止播放")
        }
    }

    fun join(player: Player, targetPlayer: Player) {
        if (!isPlaying(targetPlayer)) {
            player.sendMessage("无法发送申请, 对方没有在播放")
            return
        }
        if (isRequested(player, targetPlayer, Join)) {
            player.sendMessage("你已经发送过了一个相同的申请")
            return
        }

        pendingRequests.add(RequestInfo(player, targetPlayer, Join))

        player.sendMessage(
            """
            |申请已发送到 ${targetPlayer.name}
            |取消: /${CommandCMP.commandName} ${CommandCMP.cancel}
        """.trimMargin()
        )
        targetPlayer.sendMessage(
            """
            |${player.name} 申请和你一起听
            |同意: /${CommandCMP.commandName} ${CommandCMP.accept}
            |拒绝: /${CommandCMP.commandName} ${CommandCMP.deny}
        """.trimMargin()
        )
    }

    fun invite(player: Player, targetPlayer: Player) {
        if (!isPlaying(player)) {
            player.sendMessage("无法发送邀请, 你没有在播放")
            return
        }
        if (isRequested(player, targetPlayer, Invite)) {
            player.sendMessage("你已经发送过了一个相同的邀请")
            return
        }

        pendingRequests.add(RequestInfo(player, targetPlayer, Invite))

        player.sendMessage(
            """
            |邀请已发送到 ${targetPlayer.name}
            |取消: /${CommandCMP.commandName} ${CommandCMP.cancel}
        """.trimMargin()
        )
        targetPlayer.sendMessage(
            """
            |${player.name} 邀请你一起听
            |接受: /${CommandCMP.commandName} ${CommandCMP.accept}
            |拒绝: /${CommandCMP.commandName} ${CommandCMP.deny}
        """.trimMargin()
        )
    }

    fun accept(player: Player) {
        val request = pendingRequests.findLast { it.target == player }
        if (request == null) {
            player.sendMessage("没有待接受/同意的邀请/申请")
            return
        }

        pendingRequests.remove(request)
        when (request.type) {
            Invite -> {
                val playing = musicPlaying[request.from]
                if (playing == null) {
                    player.sendMessage("接受失败, 对方已结束播放")
                    return
                }

                if (isListeningTogether(player)) quit(player)
                if (isPlaying(player)) stop(player)

                playing.listenTogether.add(player)

                player.sendMessage("已接受 ${request.from.name} 的邀请")
                request.from.sendMessage("${player.name} 已接受你的邀请")
            }
            Join -> {
                val playing = musicPlaying[player]
                if (playing == null) {
                    player.sendMessage("同意失败, 你已结束播放")
                    return
                }

                if (isListeningTogether(player)) quit(request.from)
                if (isPlaying(player)) stop(request.from)

                playing.listenTogether.add(request.from)

                player.sendMessage("已同意 ${request.from.name} 的申请")
                request.from.sendMessage("${player.name} 已同意你的申请")
            }
        }
    }

    fun deny(player: Player) {
        val request = pendingRequests.findLast { it.target == player }
        if (request == null) {
            player.sendMessage("没有待接受/同意的邀请/申请")
            return
        }

        pendingRequests.remove(request)
        when (request.type) {
            Invite -> {
                player.sendMessage("已拒绝 ${request.from.name} 的邀请")
                request.from.sendMessage("${player.name} 已拒绝你的邀请")
            }
            Join -> {
                player.sendMessage("已拒绝 ${request.from.name} 的申请")
                request.from.sendMessage("${player.name} 已拒绝你的申请")
            }
        }
    }

    fun cancel(player: Player) {
        pendingRequests
            .filter { it.from == player }
            .forEach {
                pendingRequests.remove(it)
                when (it.type) {
                    Invite -> {
                        player.sendMessage("已取消对 ${it.from.name} 的邀请")
                        it.from.sendMessage("${player.name} 已取消邀请")
                    }
                    Join -> {
                        player.sendMessage("已取消加入 ${it.from.name} 的申请")
                        it.from.sendMessage("${player.name} 已取消申请")
                    }
                }
            }
    }

    fun quit(player: Player) {
        val playing = musicPlaying.values.find { it.listenTogether.contains(player) }
        if (playing == null) {
            player.sendMessage("无法退出, 你并没有加入一起听")
        } else {
            playing.listenTogether.remove(player)
            playing.hostPlayer.sendMessage("${player.name} 已退出一起听")
            player.sendMessage("已退出一起听")
        }
    }

    fun modify(musicInfo: MusicInfo) {
        val musicInfos = getMusics()
        musicInfos.removeIf { it.musicFileName == musicInfo.musicFileName }
        musicInfos.add(musicInfo)
        ChaosMusicPlayer.instance.setMusicInfos(musicInfos)

        musicPlaying.map { it.value }
            .filter { it.musicInfo.musicFileName == musicInfo.musicFileName }
            .forEach { it.musicInfo = musicInfo.copy() }
    }

    private fun isPlaying(player: Player): Boolean = musicPlaying[player] != null

    private fun isListeningTogether(player: Player): Boolean =
        musicPlaying.values.find { it.listenTogether.contains(player) } != null

    private fun isRequested(player: Player, targetPlayer: Player, type: RequestType): Boolean =
        pendingRequests.find { it.from == player && it.target == targetPlayer && it.type == type } != null

    fun stopAll() {
        musicPlaying.forEach { it.value.stop() }
        musicPlaying.clear()
    }

    fun updateMusicFolder(folder: File) {
        musicFolder = folder
    }

    data class RequestInfo(
        val from: Player,
        val target: Player,
        val type: RequestType,
        val timestamp: Long = System.currentTimeMillis()
    )

    enum class RequestType {
        Invite,
        Join,
    }
}