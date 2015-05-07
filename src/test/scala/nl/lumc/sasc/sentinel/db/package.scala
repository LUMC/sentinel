package nl.lumc.sasc.sentinel

import nl.lumc.sasc.sentinel.processors.RunProcessor

package object db {

  trait AllSuccessDatabaseProvider extends DatabaseProvider { this: RunProcessor => }
}
