package com.example.globalminusone.split

internal data class SplitRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val valid get() = right > left && bottom > top
    fun separate(other: SplitRect) = right <= other.left || other.right <= left || bottom <= other.top || other.bottom <= top
}
internal data class SplitTask(val id: Int, val parentId: Int?, val displayId: Int, val userId: Int,
    val mode: String, val visible: Boolean, val type: String, val packageName: String, val bounds: SplitRect?)
internal data class SplitTaskSnapshot(val tasks: List<SplitTask>, val focusedId: Int?) {
    fun leafTasks() = tasks.filter { task -> tasks.none { it.parentId == task.id } }
    fun origin(packageName: String, userId: Int): SplitTask? = leafTasks().singleOrNull {
        it.id == focusedId && it.packageName == packageName && it.userId == userId && it.displayId == 0 &&
            it.type == "standard" && it.visible && it.mode == "fullscreen"
    }
    fun target(packageName: String, userId: Int): SplitTask? = leafTasks().filter {
        it.packageName == packageName && it.userId == userId && it.displayId == 0 && it.type == "standard" && it.mode == "fullscreen"
    }.singleOrNull()
}
internal data class SplitOrigin(val packageName: String, val taskId: Int, val userId: Int, val captureId: Long)

/** Parse task hierarchy and effective activity bounds only; never retain intent data or extras. */
internal fun parseSplitTaskDump(raw: String): SplitTaskSnapshot {
    data class Node(var task: SplitTask, val indent: Int, var actualPackage: Boolean = false)
    val nodes = mutableListOf<Node>()
    val stack = mutableListOf<Node>()
    val resumedPackages = mutableMapOf<Int, String>()
    var display = -1
    var current: Node? = null
    var focused: Int? = null
    val taskHeader = Regex("""^(\s*)\* Task\{[^#\n]*#([0-9]+)\b([^}]*)}""")
    val boundsRegex = Regex("""mBounds=Rect\((-?[0-9]+),\s*(-?[0-9]+) - (-?[0-9]+),\s*(-?[0-9]+)\)""")
    for (line in raw.lineSequence()) {
        Regex("""^Display #([0-9]+)""").find(line)?.let { display = it.groupValues[1].toInt(); stack.clear(); current = null }
        val header = taskHeader.find(line)
        if (header != null) {
            val indent = header.groupValues[1].length
            while (stack.lastOrNull()?.let { it.indent >= indent } == true) stack.removeAt(stack.lastIndex)
            val tail = header.groupValues[3]
            fun field(key: String) = Regex("""(?:^|\s)""" + key + """=([^\s]+)""").find(tail)?.groupValues?.get(1)
            val node = Node(SplitTask(header.groupValues[2].toInt(), stack.lastOrNull()?.task?.id, display,
                field("U")?.toIntOrNull() ?: -1, field("mode").orEmpty(), field("visible") == "true",
                field("type").orEmpty(), "", null), indent)
            nodes += node; stack += node; current = node
        }
        if (display == 0 && (line.contains("topResumedActivity=") || line.contains("mResumedActivity:"))) {
            Regex("""\bu[0-9]+\s+([A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z0-9_]+)+)/\S+\s+t([0-9]+)\b""").find(line)?.let {
                val id = it.groupValues[2].toInt()
                resumedPackages[id] = it.groupValues[1]
                if (focused == null) focused = id
            }
        }
        current?.let { node ->
            if (!node.actualPackage && line.trimStart().startsWith("mActivityComponent=")) {
                val pkg = line.substringAfter("mActivityComponent=").substringBefore('/').trim()
                if (validSplitPackage(pkg)) { node.task = node.task.copy(packageName = pkg); node.actualPackage = true }
            }
            if (node.task.bounds == null && line.trimStart().startsWith("CurrentConfiguration=")) boundsRegex.find(line)?.let {
                node.task = node.task.copy(bounds = SplitRect(it.groupValues[1].toInt(), it.groupValues[2].toInt(), it.groupValues[3].toInt(), it.groupValues[4].toInt()))
            }
        }
    }
    // Task affinity is application-controlled and cannot identify the actual foreground application.
    return SplitTaskSnapshot(nodes.map { node -> resumedPackages[node.task.id]?.let { node.task.copy(packageName = it) } ?: node.task }, focused)
}

internal fun validSplitPackage(value: String) = value.length in 3..255 && value.matches(Regex("""[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z0-9_]+)+"""))

internal fun encodeSplitSnapshot(snapshot: SplitTaskSnapshot): String = buildString {
    append("GMO_SPLIT_TASKS_V1\n"); append("focused\t${snapshot.focusedId ?: -1}\n")
    snapshot.tasks.forEach { t ->
        append(listOf("task", t.id, t.parentId ?: -1, t.displayId, t.userId, t.mode, t.visible, t.type, t.packageName,
            t.bounds?.left ?: "?", t.bounds?.top ?: "?", t.bounds?.right ?: "?", t.bounds?.bottom ?: "?").joinToString("\t")); append('\n')
    }
}
internal fun decodeSplitSnapshot(raw: String): SplitTaskSnapshot {
    val lines = raw.lineSequence().toList()
    require(lines.firstOrNull() == "GMO_SPLIT_TASKS_V1") { "任务快照协议不匹配" }
    val focused = lines.getOrNull(1)?.takeIf { it.startsWith("focused\t") }?.substringAfter('\t')?.toIntOrNull()?.takeIf { it >= 0 }
    val tasks = lines.drop(2).filter { it.isNotEmpty() }.map { line ->
        val f = line.split('\t'); require(f.size == 13 && f[0] == "task") { "任务快照字段不完整" }
        val bounds = f[9].toIntOrNull()?.let { SplitRect(it, f[10].toInt(), f[11].toInt(), f[12].toInt()) }
        SplitTask(f[1].toInt(), f[2].toInt().takeIf { it >= 0 }, f[3].toInt(), f[4].toInt(), f[5], f[6] == "true", f[7], f[8], bounds)
    }
    require(tasks.size <= 512 && tasks.map { it.id }.distinct().size == tasks.size) { "任务快照存在重复或超限" }
    return SplitTaskSnapshot(tasks, focused)
}

/** Modern shell split uses two different stage branches under one organizer root, never freeform. */
internal fun confirmsSplitPair(snapshot: SplitTaskSnapshot, origin: SplitOrigin, targetId: Int, targetPackage: String): Boolean {
    val byId = snapshot.tasks.associateBy { it.id }
    val first = byId[origin.taskId] ?: return false
    val second = byId[targetId] ?: return false
    if (first.id == second.id || first.packageName != origin.packageName || second.packageName != targetPackage ||
        first.userId != origin.userId || second.userId != origin.userId || first.displayId != 0 || second.displayId != 0 ||
        !first.visible || !second.visible || first.mode != "multi-window" || second.mode != "multi-window" ||
        first.type != "standard" || second.type != "standard") return false
    val a = first.bounds ?: return false; val b = second.bounds ?: return false
    if (!a.valid || !b.valid || !a.separate(b) || first.parentId == second.parentId) return false
    val firstStage = first.parentId?.let(byId::get) ?: return false
    val secondStage = second.parentId?.let(byId::get) ?: return false
    val root = firstStage.parentId?.let(byId::get) ?: return false
    return secondStage.parentId == root.id && firstStage.mode == "multi-window" && secondStage.mode == "multi-window" &&
        firstStage.visible && secondStage.visible && root.visible && root.mode in setOf("fullscreen", "multi-window") &&
        root.displayId == 0 && firstStage.displayId == 0 && secondStage.displayId == 0
}

internal fun supportsSplitTaskCommands(help: String) = Regex("""(?m)^\s*moveToSideStage\s+<taskId>""").containsMatchIn(help) &&
    Regex("""(?m)^\s*removeFromSideStage\s+<taskId>""").containsMatchIn(help) && Regex("""(?m)^\s*task focus\s+<TASK_ID>""", RegexOption.IGNORE_CASE).containsMatchIn(help)
