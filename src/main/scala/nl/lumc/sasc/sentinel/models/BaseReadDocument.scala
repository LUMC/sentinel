package nl.lumc.sasc.sentinel.models

import com.novus.salat.annotations.Salat

@Salat abstract class BaseReadDocument {

  def file: BaseFileDocument
}
