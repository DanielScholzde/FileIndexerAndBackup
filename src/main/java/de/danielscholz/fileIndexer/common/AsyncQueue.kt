package de.danielscholz.fileIndexer.common

//object AsyncQueue {
//   val MAX = Runtime.getRuntime().availableProcessors();
//   private val tasks = mutableListOf<CompletableFuture<Void>>()
//
//   fun runAsync(task: () -> Unit) {
//      synchronized(this) {
//         tasks.removeAll { it.isDone }
//         if (tasks.size < MAX) {
//            tasks.add(CompletableFuture.runAsync(task))
//            return
//         }
//      }
//      task()
//   }
//
//   fun isDone() = getWorking() == 0
//
//   fun getWorking(): Int {
//      synchronized(this) {
//         return tasks.count { !it.isDone }
//      }
//   }
//
//   fun waitAllDone() {
//      synchronized(this) {
//         tasks.all { it.join(); true }
//      }
//   }
//}