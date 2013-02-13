/*!
* jQuery collapsible list plugin
*
* The collapsible method is applied on the parent element containing the collapsible content element. It will hide the
* collapsible element in a case, when its children can't fit into it (the sum of children element widths is larger than
* the parent element width). If the navigationElement property is specified in the options, this will serve as a show/hide
* button for this collapsible element after the element is collapsed. Collapsing and expanding means applying the given
* class to the collapsible element.
*
* Depends:
* jQuery
*/

(function($){
$.fn.collapsible = function(options) {

    // We will need the selector for the resize function
    var parentElement = this;

    // Overriding default options by user options
    var settings = $.extend({}, {
        // Selector for the show/hide element.
        navigationElement: ".collapsibleToggle",
        // Selector for the to-be-collapsed element inside this parent element.
        collapsibleElement: ".collapsibleContent",
        // Class which is set to the to-be-collapsed element when it's collapsed.
        collapsedClass: "collapsed",
        // Class which is set to the to-be-collapsed element when it's expanded.
        expandedClass: "expanded",
        // Class which is toggled for the show/hide element when it's collapsed/expanded.
        visibleClass: "visible-element",
        minWidthEm: 30
    }, options);

    var collapsibleElement = $(this).children(settings.collapsibleElement);

    // Setting the navigation show/hide button
    $(settings.navigationElement).click(function(){
        collapsibleElement.toggleClass(settings.visibleClass);
        $(this).toggleClass(settings.visibleClass);
    });

    console&&console.log("wide screen");

    // Sum of widths of container children
    var collapsibleElementWidth = 0;

    collapsibleElement.children().each(function() {
        // Using the true parameter in outerWidth method will include margins
        collapsibleElementWidth += $(this).outerWidth(true);
    });

    // Check if the collapsible element fits into the parent element
    checkSize(parentElement);

    // Do the check above after each resizing of window
    $(window).resize(function(){
        checkSize(parentElement);
    });

    // Function used to collapse/expand any given element
    function collapse(selector, state) {
        $(selector).toggleClass(settings.collapsedClass, state);
        $(selector).toggleClass(settings.expandedClass, !state);
    }

    // Check the size of the main container after each window resize
    function checkSize(selector) {

        // Always show navigation show/hide button and don't collapse on small screens
        if (!isWidthValid()){
            $(settings.navigationElement).show();
            return;
        }

        var parentWidth = $(selector).outerWidth(true);
        var collapsibleElement = $(selector).children(settings.collapsibleElement);

        // If collapsible element doesn't fit into the parent element, collapse it
        if (parentWidth < collapsibleElementWidth) {
            console&&console.log("collapse");
            collapse(collapsibleElement, true);
            $(settings.navigationElement).show();
        }
        // If collapsible element does fit into the parent element, expand it
        else {
            console&&console.log("expand");
            collapse(collapsibleElement, false);
            $(settings.navigationElement).hide();
        }
    }

    // Getting the document font-size magic from
    // http://stackoverflow.com/a/10465984
    function getFontSize(){
        return Number(getComputedStyle(document.body, "").fontSize.match(/(\d*(\.\d*)?)px/)[1]);
    }

    function isWidthValid(){
        var minimalScreenWidth = settings.minWidthEm * getFontSize();
        var currentScreenWidth = $(document.body).width();
        console&&console.log("minimal : " + minimalScreenWidth);
        console&&console.log("current : " + currentScreenWidth);

        return (currentScreenWidth > minimalScreenWidth)
    }
};

})(jQuery);
