package ysap

class Page {
    String name //Page name
    String description
    String content //Page content

    List links

    static hasMany = [links: Link]

    static constraints = {
        content maxSize: 6000
    }
}
