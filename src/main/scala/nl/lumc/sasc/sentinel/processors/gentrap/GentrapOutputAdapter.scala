package nl.lumc.sasc.sentinel.processors.gentrap

import nl.lumc.sasc.sentinel.db.DatabaseProvider
import nl.lumc.sasc.sentinel.processors.OutputAdapter

trait GentrapOutputAdapter extends OutputAdapter { this: DatabaseProvider =>

}
