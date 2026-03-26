// @GENERATOR:play-routes-compiler
// @SOURCE:D:/javaproject/IT_coursework-master/ITSD-DT2025-26-Template/conf/routes
// @DATE:Wed Mar 25 21:14:56 CST 2026


package router {
  object RoutesPrefix {
    private var _prefix: String = "/"
    def setPrefix(p: String): Unit = {
      _prefix = p
    }
    def prefix: String = _prefix
    val byNamePrefix: Function0[String] = { () => prefix }
  }
}
