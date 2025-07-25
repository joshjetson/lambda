package ysap

import grails.testing.mixin.integration.Integration
import grails.gorm.transactions.Rollback
import spock.lang.Specification
import org.hibernate.SessionFactory

@Integration
@Rollback
class PageServiceSpec extends Specification {

    PageService pageService
    SessionFactory sessionFactory

    private Long setupData() {
        // TODO: Populate valid domain instances and return a valid ID
        //new Page(...).save(flush: true, failOnError: true)
        //new Page(...).save(flush: true, failOnError: true)
        //Page page = new Page(...).save(flush: true, failOnError: true)
        //new Page(...).save(flush: true, failOnError: true)
        //new Page(...).save(flush: true, failOnError: true)
        assert false, "TODO: Provide a setupData() implementation for this generated test suite"
        //page.id
    }

    void "test get"() {
        setupData()

        expect:
        pageService.get(1) != null
    }

    void "test list"() {
        setupData()

        when:
        List<Page> pageList = pageService.list(max: 2, offset: 2)

        then:
        pageList.size() == 2
        assert false, "TODO: Verify the correct instances are returned"
    }

    void "test count"() {
        setupData()

        expect:
        pageService.count() == 5
    }

    void "test delete"() {
        Long pageId = setupData()

        expect:
        pageService.count() == 5

        when:
        pageService.delete(pageId)
        sessionFactory.currentSession.flush()

        then:
        pageService.count() == 4
    }

    void "test save"() {
        when:
        assert false, "TODO: Provide a valid instance to save"
        Page page = new Page()
        pageService.save(page)

        then:
        page.id != null
    }
}
