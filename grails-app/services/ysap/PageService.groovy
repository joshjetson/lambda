package ysap

import grails.gorm.services.Service

@Service(Page)
interface PageService {

    Page get(Serializable id)

    List<Page> list(Map args)

    Long count()

    void delete(Serializable id)

    Page save(Page page)

}